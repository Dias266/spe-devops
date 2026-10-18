package application.port;

/**
 * Port onto the Vehicle Telemetry &amp; Health context: lets the MAS hand a
 * telemetry update to the IoT control unit, which publishes it on the MQTT
 * topic {@code vehicle/telemetry}.
 *
 * The coordinator uses it after a service is completed (see
 * {@code +service_completed_Mqtt} in fleet_coordinator_agent.asl) to push the
 * vehicle's post-service reading back to the edge.
 */
public interface TelemetryPublisherPort {

    /**
     * Sends one telemetry update.
     *
     * @param topic       the topic the agent asked for (kept for logging; the
     *                    control unit always publishes on vehicle/telemetry)
     * @param jsonPayload JSON object with vin, temp, mileage and state
     * @return true if the control unit accepted and published it
     */
    boolean publish(String topic, String jsonPayload);
}
