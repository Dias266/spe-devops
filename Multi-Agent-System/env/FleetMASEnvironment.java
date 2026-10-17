package env;

import jason.asSyntax.*;
import jason.environment.Environment;
import java.util.*;
import java.util.logging.Logger;

import org.json.JSONObject;

import application.port.BlockchainPort;
import application.port.MLPredictionPort;
import application.port.MaintenanceInsight;
import application.port.ServiceRecordRepository;
import application.usecase.EndorseServiceRecord;
import application.usecase.LogFleetAnomaly;
import application.usecase.RegisterVehicle;
import infrastructure.InMemoryServiceRecordRepository;
import infrastructure.MockMLPipeline;
import infrastructure.RealBlockchainClient;

/**
 * FleetMASEnvironment
 *
 * Bridges Jason MAS to the three integrated course layers:
 *   - IoT layer       (Codice 77780) — MQTT broker at localhost:1883
 *   - ML layer        (Codice 95631) — Flask API at localhost:5000
 *   - Blockchain      (Codice 87474) — DS backend at localhost:3000
 *
 * This is the thinned version after the DDD/Clean-Architecture pass, built
 * against the actual current env/ folder (10 files, post file-split):
 * IoTAnomaly.java, IoTStreamAdapter.java and MockIoTStream.java already
 * exist here as their own top-level files (same package, so no import
 * needed) — this class does NOT redeclare them as nested classes, since
 * that would create a second, unrelated set of types with the same simple
 * names and leave the real files as dead code.
 *
 * What changed and why, versus the 393-line split-only version this
 * replaces:
 *
 *  - Blockchain and ML integration are now behind BlockchainPort /
 *    MLPredictionPort (application/port/), implemented in infrastructure/.
 *    This class only translates Jason Structures into calls on those
 *    ports/use cases and results back into percepts.
 *  - logFleetAnomalyToBlockchain and registerVehicle now delegate to
 *    application/usecase/ classes (LogFleetAnomaly, RegisterVehicle)
 *    instead of calling an adapter directly inline.
 *  - MLInsight.java, MLPipelineAdapter.java and BlockchainAdapter.java
 *    (the three interfaces/data classes that were sitting in env/ after
 *    the split) are now DEAD once this file is dropped in — nothing here
 *    references them any more. Delete them from env/, see the message
 *    accompanying this file for the exact file list.
 *  - RealBlockchainClient.java and MockMLPipeline.java move OUT of env/
 *    entirely, into infrastructure/ (delivered earlier), retrofitted to
 *    implement BlockchainPort / MLPredictionPort instead of the old
 *    env.*Adapter interfaces.
 *  - MockBlockchainClient.java (in env/ after the split, never actually
 *    instantiated by init()) also moves to infrastructure/.
 *  - IoTAnomaly.java, IoTStreamAdapter.java, MockIoTStream.java are
 *    UNCHANGED and STAY in env/ exactly as they are — no IoTStreamPort
 *    exists yet, so there's nothing to move them to.
 *  - Three legacy handler methods — handleMQTT, handleMQTT2, handleMQTT3
 *    — are REMOVED. They had zero call sites in the original file (only
 *    handleMQTT4 was ever invoked from executeAction), so this is dead
 *    code removal, not a behaviour change.
 *  - writeServiceRecord is NOT yet routed through BlockchainPort — see
 *    the comment on that case below for why, and what would need to
 *    change on the .asl side first.
 *  - The pre-existing bug where both branches of USE_REAL_INTEGRATIONS
 *    constructed RealBlockchainClient (making the mock/real toggle a
 *    no-op for blockchain) is PRESERVED as-is in this pass, flagged
 *    below rather than silently fixed, so this step's behaviour stays
 *    identical to before it for regression-testing purposes.
 */
public class FleetMASEnvironment extends Environment {

    private static final Logger logger = Logger.getLogger(FleetMASEnvironment.class.getName());

    // -------------------------------------------------------------------------
    // SET true  → connects to real DS backend at localhost:3000
    // SET false → uses mock data (standalone simulation)
    // Default is false so the MAS runs end-to-end with no external backends.
    // -------------------------------------------------------------------------
    private static final boolean USE_REAL_INTEGRATIONS = false;

    // DS project endpoint (from start-all.sh output)
    private static final String DS_BACKEND_URL =
            System.getenv().getOrDefault("IOT_BACKEND_URL", "http://localhost:3002");

    private IoTStreamAdapter iotAdapter;

    private BlockchainPort blockchainPort;
    private MLPredictionPort mlPredictionPort;

    private LogFleetAnomaly logFleetAnomalyUseCase;
    private RegisterVehicle registerVehicleUseCase;
    private EndorseServiceRecord endorseServiceRecordUseCase;

    private Thread tickThread;
    private volatile boolean running = true;

    // -------------------------------------------------------------------------
    // Lifecycle — this is the composition root: the one place allowed to
    // know about concrete infrastructure classes and wire them into ports.
    // -------------------------------------------------------------------------

    @Override
    public void init(String[] args) {
        logger.info("[ENV] FleetMASEnvironment initialising...");

        iotAdapter = new MockIoTStream();

        if (USE_REAL_INTEGRATIONS) {
            logger.info("[ENV] Mode: REAL — connecting to DS backend at " + DS_BACKEND_URL);
            // NOTE — preserved pre-existing bug, not fixed in this pass:
            // both branches construct RealBlockchainClient. The original
            // file did the same (its MockBlockchainClient existed but was
            // never actually instantiated anywhere). Flip this branch to
            // `new infrastructure.MockBlockchainClient()` once you're
            // ready for the toggle to do something real.
            blockchainPort = new RealBlockchainClient(DS_BACKEND_URL);
        } else {
            logger.info("[ENV] Mode: MOCK — using simulated data");
            blockchainPort = new RealBlockchainClient(DS_BACKEND_URL);
        }
        mlPredictionPort = new MockMLPipeline();

        ServiceRecordRepository serviceRecordRepository = new InMemoryServiceRecordRepository();

        logFleetAnomalyUseCase = new LogFleetAnomaly(blockchainPort);
        registerVehicleUseCase = new RegisterVehicle();
        endorseServiceRecordUseCase = new EndorseServiceRecord(serviceRecordRepository);

        startTickThread();
        logger.info("[ENV] FleetMASEnvironment ready.");
    }

    @Override
    public void stop() {
        running = false;
        if (tickThread != null) tickThread.interrupt();
        super.stop();
    }

    // -------------------------------------------------------------------------
    // Action Dispatcher
    // -------------------------------------------------------------------------

    @Override
    public boolean executeAction(String agentName, Structure action) {
        String functor = action.getFunctor();
        logger.info("[ENV] Action from " + agentName + ": " + functor);

        switch (functor) {

            case "registerCoordinator":
                logger.info("[ENV] FleetCoordinator registered.");
                return true;

            case "pollIoTAnomalyStream":
                return handlePollIoT(agentName);

            case "logFleetAnomalyToBlockchainLoop":
                logger.info("[ENV] FleetCoordinator logFleetAnomalyToBlockchain Lopp.");
                // falls through intentionally, same as the original file

            case "logFleetAnomalyToBlockchain": {
                String anomalyType = action.getTerm(0).toString();
                int count;
                try {
                    count = Integer.parseInt(action.getTerm(1).toString());
                } catch (NumberFormatException e) {
                    logger.warning("[ENV] logFleetAnomalyToBlockchain: bad count term — " + e.getMessage());
                    return false;
                }

                // Preserved from the original — worth flagging: this passes
                // the anomaly TYPE (term 0) as if it were a vehicle VIN,
                // which handleReadDigitalTwin treats as a VIN lookup. That
                // looks like a copy-paste bug in the original code, not
                // intentional behaviour. Kept as-is here so this step's
                // output stays identical to before it; fix separately once
                // confirmed unnecessary.
                handleReadDigitalTwin(agentName, action.getTerm(0).toString());

                try {
                    return logFleetAnomalyUseCase.execute(anomalyType, count);
                } catch (IllegalArgumentException e) {
                    // FleetAnomalyDetected's constructor validates anomalyType
                    // and vehicleCount — a case that could never happen
                    // before this refactor, since nothing checked either
                    // value previously.
                    logger.warning("[ENV] logFleetAnomalyToBlockchain rejected: " + e.getMessage());
                    return false;
                }
            }

            case "registerVehicle": {
                String vin = action.getTerm(0).toString();
                try {
                    boolean ok = registerVehicleUseCase.execute(vin);
                    if (ok) {
                        addPercept("fleet_coordinator_agent",
                                ASSyntax.createLiteral("vehicle_registered",
                                        ASSyntax.createAtom(vin)));
                    }
                    return ok;
                } catch (IllegalArgumentException e) {
                    // Blank VIN — again, a case the original code never
                    // checked for at all (it always returned true).
                    logger.warning("[ENV] registerVehicle rejected: " + e.getMessage());
                    return false;
                }
            }

            // -----------------------------------------------------------------
            // Edge / ML / crypto simulation hooks.
            // Jason environment actions cannot bind result variables back into a
            // plan, so the VehicleAgent simulates the actual values internally
            // and these actions simply acknowledge success. Implemented here so
            // that any agent invoking them never hits the "Unknown action" path.
            // Unchanged by this refactor — see MLPredictionPort's Javadoc.
            // -----------------------------------------------------------------
            case "fetchEdgeSensors":
            case "evaluateRandomForest":
            case "evaluateIsolationForest":
            case "deriveECDSAKey":
            case "signTelemetryRecord":
                logger.fine("[ENV] Simulated edge/ML/crypto action: " + functor);
                return true;

            case "fetchMLHealthInsights":
                return handleFetchMLInsights(agentName);

            case "readDigitalTwin":
                return handleReadDigitalTwin(agentName, action.getTerm(0).toString());

            case "sendMQTTMessage": {
                // Unchanged — no domain model exists yet for raw MQTT
                // publishing, so this stays a direct pass-through.
                String topic = action.getTerm(0).toString();
                String payload = action.getTerm(1).toString();
                return handleMQTT4(topic, payload);
            }

            case "writeServiceRecord": {
                // NOT YET routed through BlockchainPort. BlockchainPort's
                // writeServiceRecord takes a full domain.model.ServiceRecord
                // (id, vehicleId, part, technician, cost) — but this Jason
                // action only ever carries (VehicleID, details-atom); see
                // service_center_agent.asl's
                //   writeServiceRecord(VehicleID, scheduled_maintenance);
                // None of the other fields a ServiceRecord needs are passed
                // across this call. Building one here would mean fabricating
                // data (a fake id, fake part/technician/cost) just to satisfy
                // the port's signature, which would be worse than leaving
                // this one case unconverted. Wiring it properly needs the
                // .asl call site changed to pass RID/Part/Tech/Cost too —
                // out of scope for this file-only edit. Behaviour is
                // preserved as-is (the previous adapter's writeServiceRecord
                // was itself a stub that always returned true and ignored
                // its inputs, so nothing observable is lost by not routing
                // this through the new port yet).
                String vehicleId = action.getTerm(0).toString();
                boolean ok = true;
                if (ok) {
                    addPercept("fleet_coordinator_agent",
                            ASSyntax.createLiteral("booking_confirmed",
                                    ASSyntax.createAtom(vehicleId)));
                }
                return ok;
            }

            default:
                logger.warning("[ENV] Unknown action: " + functor);
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // Internal Handlers
    // -------------------------------------------------------------------------

    private boolean handleMQTT4(String topic, String payload) {
        try {
            String cleanPayload = payload;
            if (cleanPayload.startsWith("\"") && cleanPayload.endsWith("\"")) {
                cleanPayload = cleanPayload.substring(1, cleanPayload.length() - 1);
            }
            cleanPayload = cleanPayload.replace("\\\"", "\"");

            logger.info("[ENV] Sending payload to API: " + cleanPayload);

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(DS_BACKEND_URL + "/api/publish-telemetry"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(cleanPayload))
                    .build();

            java.net.http.HttpResponse<String> response =
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("[ENV] API call successful: " + response.body());
                return true;
            } else {
                logger.warning("[ENV] API call failed, status: " + response.statusCode());
                return false;
            }

        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "[ENV] HTTP request failed", e);
            return false;
        }
    }

    private boolean handlePollIoT(String agentName) {
        List<IoTAnomaly> anomalies = iotAdapter.getLatestAnomalies();
        for (IoTAnomaly a : anomalies) {
            Literal percept = ASSyntax.createLiteral("anomaly_detected",
                    ASSyntax.createAtom(a.vehicleId),
                    ASSyntax.createAtom(a.anomalyType),
                    ASSyntax.createAtom(a.severity));
            addPercept("fleet_coordinator_agent", percept);
            logger.info("[ENV] Injected anomaly_detected(" +
                    a.vehicleId + "," + a.anomalyType + "," + a.severity + ")");
        }
        return true;
    }

    private boolean handleFetchMLInsights(String agentName) {
        MaintenanceInsight insight = mlPredictionPort.getInsight(agentName);
        if (insight == null) return false;
        addPercept(agentName, ASSyntax.createLiteral("health_status",
                ASSyntax.createAtom(insight.componentId()),
                ASSyntax.createNumber(insight.score())));
        addPercept(agentName, ASSyntax.createLiteral("urgency_level",
                ASSyntax.createAtom(insight.urgency())));
        return true;
    }

    private boolean handleReadDigitalTwin(String agentName, String vin) {
        System.out.println("[BLOCKCHAIN-REAL] REAL digital twin read for " + agentName + " (VIN: " + vin + ")");

        String history = blockchainPort.readDigitalTwin(vin);

        System.out.println("[BLOCKCHAIN-REAL] history for " + history);

        JSONObject jsonObject = new JSONObject(history);
        String actualVin = jsonObject.getJSONObject("data").getString("vin");

        Literal percept = ASSyntax.createLiteral("test_fleet",
                ASSyntax.createAtom(actualVin),
                ASSyntax.createAtom(history)
        );

        addPercept(agentName, percept);

        System.out.println("[BLOCKCHAIN-REAL] Percept 'test_fleet' added to " + agentName);

        return true;
    }

    // -------------------------------------------------------------------------
    // Tick Thread
    // -------------------------------------------------------------------------

    private void startTickThread() {
        tickThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(2000);
                    addPercept("fleet_coordinator_agent",
                            ASSyntax.createLiteral("tick"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }
}
