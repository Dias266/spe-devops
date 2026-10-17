package env;

class IoTAnomaly {
    String vehicleId;
    String anomalyType;
    String severity;

    IoTAnomaly(String v, String t, String s) {
        vehicleId = v;
        anomalyType = t;
        severity = s;
    }
}
