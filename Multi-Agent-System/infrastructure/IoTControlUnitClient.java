package infrastructure;

import application.port.TelemetryPublisherPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP adapter for {@link TelemetryPublisherPort}: POSTs the payload to the
 * IoT control unit (Embedded-Systems-gs-main/control-unit-iot.js), which turns
 * it into an MQTT message on {@code vehicle/telemetry}.
 *
 * Moved out of FleetMASEnvironment#handleMQTT4 so the Java side of the
 * Java-to-IoT connection can be tested without Jason. The request is the same
 * as before; a connect/request timeout was added so an unreachable control
 * unit cannot block an agent's action forever.
 */
public final class IoTControlUnitClient implements TelemetryPublisherPort {

    /** Endpoint on the control unit that publishes to MQTT. */
    public static final String PUBLISH_PATH = "/api/publish-telemetry";

    private static final Logger logger = Logger.getLogger(IoTControlUnitClient.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String baseUrl;

    public IoTControlUnitClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public boolean publish(String topic, String jsonPayload) {
        try {
            logger.info("[ENV] Sending payload to API (topic " + topic + "): " + jsonPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + PUBLISH_PATH))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("[ENV] API call successful: " + response.body());
                return true;
            }
            logger.warning("[ENV] API call failed, status: " + response.statusCode());
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "[ENV] HTTP request interrupted", e);
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ENV] HTTP request failed", e);
            return false;
        }
    }

    /**
     * Turns a Jason string term into the raw JSON text: strips the surrounding
     * quotes and un-escapes the inner ones. Jason passes
     * {@code "{\"vin\": \"v1\"}"}; the control unit expects {@code {"vin": "v1"}}.
     */
    public static String unquoteJasonString(String term) {
        String clean = term;
        if (clean.length() >= 2 && clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean.replace("\\\"", "\"");
    }
}
