import os
import re
import jpype
import jpype.imports
from jpype.types import *

# --- تنظیمات و مسیرها ---
MODEL_FILE = "AdaBoostM1-DecisionStump-base-classifier.model"
DATA_FILE = "data.arff"
DEFAULT_JAR_FILE = "weka.jar"
TEMPLATE_FILE = "training_template.arff"
LOG_FILE = "final_predictions_log.txt"
# Shared with the dashboard backend; in Docker both mount the same volume.
LOG_FILE_dashboard = os.environ.get(
    "DASHBOARD_LOG",
    "../MAS_system_node_server_on-main/back/backend/final_predictions_log.txt",
)


# PostgreSQL address, e.g. postgresql://fleet:fleet@postgres:5432/fleet in Docker.
# When it is not set, predictions are only written to the log files.
DATABASE_URL = os.environ.get("DATABASE_URL")


def save_to_database(instance_num, instance_data, raw_prediction, confidence, decision):
    """Stores one prediction in the maintenance_prediction table.

    Returns True when a row was written, False when no database is configured.
    """
    if not DATABASE_URL:
        return False
    import psycopg

    with psycopg.connect(DATABASE_URL, connect_timeout=5) as conn:
        conn.execute(
            "INSERT INTO maintenance_prediction "
            "(instance_number, instance_data, raw_prediction, confidence, decision) "
            "VALUES (%s, %s, %s, %s, %s)",
            (
                int(instance_num),
                instance_data,
                raw_prediction,
                round(confidence, 1),
                decision,
            ),
        )
    return True


def locate_project_file(filename):
    if os.path.exists(filename):
        return filename
    parent_path = os.path.join("..", filename)
    if os.path.exists(parent_path):
        return parent_path
    return None


def start_jvm_if_needed():
    """راه‌اندازی ماشین مجازی جاوا با آرگومان‌های لازم و کلاس‌پث Weka"""
    if not jpype.isJVMStarted():
        jar_path = locate_project_file(DEFAULT_JAR_FILE)
        if not jar_path:
            raise FileNotFoundError(f"❌ Error: Cannot find '{DEFAULT_JAR_FILE}'.")

        # ۱. مجوز دادن به Weka برای دسترسی به ماژول‌های داخلی جاوا
        jvm_flags = [
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
        ]

        # ۲. اضافه کردن فایل‌های JAR موجود در همان پوشه (برای کتابخانه‌های ماتریسی MTJ)
        jar_dir = os.path.dirname(os.path.abspath(jar_path))
        classpath = [jar_path, os.path.join(jar_dir, "*")]

        jpype.startJVM(*jvm_flags, classpath=classpath)


def extract_selected_instance():
    template_path = locate_project_file(TEMPLATE_FILE)
    if not template_path:
        print(f"❌ Error: Cannot find template file '{TEMPLATE_FILE}'.")
        return None, None, None

    header_lines = []
    data_lines = []
    in_data_section = False

    with open(template_path, "r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if not stripped:
                if not in_data_section:
                    header_lines.append(line)
                continue

            if stripped.lower().startswith("@data"):
                in_data_section = True
                header_lines.append(line)
                continue

            if in_data_section:
                if not stripped.startswith("%"):
                    data_lines.append(stripped)
            else:
                header_lines.append(line)

    if not data_lines:
        print("❌ Error: The template contains no data rows.")
        return None, None, None

    total_instances = len(data_lines)
    print(
        f"📋 Found template data! There are {total_instances} total instances available."
    )

    while True:
        try:
            choice = input(
                f"➔ Enter the instance number you want to predict (1 to {total_instances}): "
            ).strip()
            idx = int(choice) - 1
            if 0 <= idx < total_instances:
                target_instance = data_lines[idx]
                break
            else:
                print(
                    f"⚠️ Invalid number. Pick a number between 1 and {total_instances}."
                )
        except ValueError:
            print("⚠️ Please enter a valid whole number.")

    parts = target_instance.split(",")
    if parts:
        parts.pop()  # حذف کلاس فعلی/اصلی
        new_data_row = ",".join(parts) + ",?"
        return "".join(header_lines), new_data_row, choice

    return None, None, None


def main():
    print("=== Interactive Weka Predictor with 70% Filter (JPype Version) ===")

    header, test_row, instance_num = extract_selected_instance()
    if not header or not test_row:
        return

    # ذخیره فایل داده موقت
    with open(DATA_FILE, "w", encoding="utf-8") as f:
        f.write(header + "\n" + test_row + "\n")

    print("\nProcessing instance through AdaBoost engine via JPype...")

    active_model = locate_project_file(MODEL_FILE)
    if not active_model:
        print(f"❌ Error: Missing model file '{MODEL_FILE}'.")
        return

    try:
        # ۱. راه‌اندازی ماشین مجازی جاوا (JVM)
        start_jvm_if_needed()

        # ۲. ایمپورت کلاس‌های جاوا از داخل Weka
        from weka.core import SerializationHelper
        from weka.core.converters import ConverterUtils

        # ۳. لود کردن مدل پیش‌بینی
        classifier = SerializationHelper.read(active_model)

        # ۴. خواندن فایل ARFF داده پیش‌بینی
        source = ConverterUtils.DataSource(DATA_FILE)
        dataset = source.getDataSet()

        # تعیین آخرین ستون به عنوان کلاس هدف
        if dataset.classIndex() == -1:
            dataset.setClassIndex(dataset.numAttributes() - 1)

        # گرفتن اولین نمونه (Instance)
        test_instance = dataset.instance(0)

        # ۵. محاسبه پیش‌بینی و میزان اطمینان (Confidence)
        prediction_index = int(classifier.classifyInstance(test_instance))
        distribution = classifier.distributionForInstance(test_instance)

        # مقدار خام کلاس پیش‌بینی شده و درصد اطمینان مدل
        weka_prediction = str(dataset.classAttribute().value(prediction_index))
        raw_confidence = distribution[prediction_index]
        confidence_percentage = raw_confidence * 100

        print("\n--- 📊 Analysis Results ---")
        print(f"Weka Raw Prediction: {weka_prediction}")
        print(f"Model Confidence: {confidence_percentage:.1f}%")

        # --- اعمال منطق آستانه ۷۰ درصد ---
        if weka_prediction == "1" and confidence_percentage < 70.0:
            explanation = "Maintenance 0 (No Maintenance Needed) - Reason: Confidence was lower than 70%"
        elif weka_prediction == "1" and confidence_percentage >= 70.0:
            explanation = (
                "Maintenance 1 (Needs Maintenance) - Reason: High confidence prediction"
            )
        else:
            explanation = (
                f"Maintenance {weka_prediction} - Reason: Native model decision"
            )

        print(f"\n🔮 Final Filtered Decision: {explanation}")
        print("--------------------------------")

        # --- ثبت در فایل‌های لاگ ---
        log_entry = (
            f"Instance Number: {instance_num}\n"
            f"Instance Data: {test_row}\n"
            f"Weka Raw Prediction: {weka_prediction}\n"
            f"Model Confidence: {confidence_percentage:.1f}%\n"
            f"Final Adjusted Decision: {explanation}\n"
            f"{'='*40}\n"
        )

        with open(LOG_FILE, "a", encoding="utf-8") as log_f:
            log_f.write(log_entry)

        # ذخیره در صورت وجود مسیر دشبورد
        try:
            with open(LOG_FILE_dashboard, "a", encoding="utf-8") as log_f:
                log_f.write(log_entry)
        except FileNotFoundError:
            pass

        print(f"🎉 Results successfully appended to file: {LOG_FILE}\n")

        if save_to_database(
            instance_num,
            test_row,
            weka_prediction,
            confidence_percentage,
            explanation,
        ):
            print("🗄  Prediction stored in PostgreSQL (maintenance_prediction)\n")

    except Exception as e:
        print(f"\n❌ Execution Error using JPype: {e}")
    finally:
        # متوقف کردن JVM در صورت نیاز (اختیاری)
        if jpype.isJVMStarted():
            jpype.shutdownJVM()


if __name__ == "__main__":
    main()
