package infrastructure;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the Java side of the MAS-to-IoT connection.
 *
 * A small HTTP server stands in for control-unit-iot.js and records what the
 * client sends. The Node side of the same contract (that this request ends up
 * on MQTT vehicle/telemetry) is tested in Embedded-Systems-gs-main/test/mqtt.test.js
 * with the same payload.
 */
class IoTControlUnitClientTest {

    /** What fleet_coordinator_agent.asl builds in +service_completed_Mqtt, after unquoting. */
    private static final String COORDINATOR_PAYLOAD =
            "{\"vin\": \"vehicle_agent1\", \"temp\": \"22\", \"mileage\": \"2000\", \"state\": \"NORMAL\"}";

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int statusToReturn = 200;

    @BeforeEach
    void startFakeControlUnit() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusToReturn, reply.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(reply);
            }
        });
        server.start();
    }

    @AfterEach
    void stopFakeControlUnit() {
        server.stop(0);
    }

    private IoTControlUnitClient client() {
        return new IoTControlUnitClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void postsJsonToThePublishEndpoint() {
        boolean ok = client().publish("fleet/vehicles/vehicle_agent1/fix", COORDINATOR_PAYLOAD);

        assertTrue(ok);
        assertEquals("POST", method.get());
        assertEquals("/api/publish-telemetry", path.get());
        assertEquals("application/json", contentType.get());
        assertEquals(COORDINATOR_PAYLOAD, body.get());
    }

    @Test
    void reportsFailureWhenControlUnitCannotReachTheBroker() {
        statusToReturn = 503; // what control-unit-iot.js answers when MQTT is down

        assertFalse(client().publish("t", COORDINATOR_PAYLOAD));
    }

    @Test
    void reportsFailureWhenFieldsAreMissing() {
        statusToReturn = 400;

        assertFalse(client().publish("t", "{\"vin\": \"v1\"}"));
    }

    @Test
    void reportsFailureWhenControlUnitIsUnreachable() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertFalse(new IoTControlUnitClient("http://127.0.0.1:" + freePort).publish("t", COORDINATOR_PAYLOAD));
    }

    @Test
    void unquotesTheJasonStringTerm() {
        String jasonTerm = "\"{\\\"vin\\\": \\\"vehicle_agent1\\\", \\\"temp\\\": \\\"22\\\", "
                + "\\\"mileage\\\": \\\"2000\\\", \\\"state\\\": \\\"NORMAL\\\"}\"";

        assertEquals(COORDINATOR_PAYLOAD, IoTControlUnitClient.unquoteJasonString(jasonTerm));
    }

    @Test
    void leavesPlainJsonUntouched() {
        assertEquals(COORDINATOR_PAYLOAD, IoTControlUnitClient.unquoteJasonString(COORDINATOR_PAYLOAD));
    }
}
