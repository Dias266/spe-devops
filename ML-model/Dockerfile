# Maintenance prediction service: Python + Weka model loaded through JPype (needs a JVM).
FROM python:3.11-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends default-jre-headless \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app/ML-model
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py weka.jar data.arff training_template.arff AdaBoostM1-DecisionStump-base-classifier.model ./
# The script asks which instance to predict; INSTANCE picks it non-interactively.
ENV INSTANCE=8 \
    DASHBOARD_LOG=/shared/final_predictions_log.txt
CMD ["sh", "-c", "mkdir -p /shared && echo \"$INSTANCE\" | python main.py"]
