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
import application.usecase.LogServiceRecord;
import application.usecase.RegisterVehicle;
import application.port.TelemetryPublisherPort;
import domain.event.ServiceRecordEndorsed;
import domain.model.ServiceRecord;
import infrastructure.InMemoryServiceRecordRepository;
import infrastructure.IoTControlUnitClient;
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
 *  - writeServiceRecord now carries (RID, Vehicle, Part, Tech, Cost) and goes
 *    through the LogServiceRecord use case (repository + BlockchainPort);
 *    the endorsement reply goes through EndorseServiceRecord via the new
 *    endorseServiceRecord action.
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
    private TelemetryPublisherPort telemetryPublisher;

    private LogFleetAnomaly logFleetAnomalyUseCase;
    private RegisterVehicle registerVehicleUseCase;
    private EndorseServiceRecord endorseServiceRecordUseCase;
    private LogServiceRecord logServiceRecordUseCase;

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
        telemetryPublisher = new IoTControlUnitClient(DS_BACKEND_URL);

        ServiceRecordRepository serviceRecordRepository = new InMemoryServiceRecordRepository();

        logFleetAnomalyUseCase = new LogFleetAnomaly(blockchainPort);
        registerVehicleUseCase = new RegisterVehicle();
        endorseServiceRecordUseCase = new EndorseServiceRecord(serviceRecordRepository);
        logServiceRecordUseCase = new LogServiceRecord(serviceRecordRepository, blockchainPort);

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
                // Goes through TelemetryPublisherPort to the IoT control
                // unit, which publishes it on MQTT vehicle/telemetry.
                String topic = action.getTerm(0).toString();
                String payload = action.getTerm(1).toString();
                return handleMQTT4(topic, payload);
            }

            case "writeServiceRecord":
                // writeServiceRecord(RID, Vehicle, Part, Tech, Cost) from
                // service_center_agent.asl, when a booking is accepted.
                return handleWriteServiceRecord(action);

            case "endorseServiceRecord":
                // endorseServiceRecord(RID, Endorser, approve|reject) from
                // service_center_agent.asl, when the endorsement reply arrives.
                return handleEndorseServiceRecord(action);

            default:
                logger.warning("[ENV] Unknown action: " + functor);
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // Internal Handlers
    // -------------------------------------------------------------------------

    private boolean handleMQTT4(String topic, String payload) {
        // The HTTP call lives in infrastructure.IoTControlUnitClient; this
        // class only turns the Jason string term back into plain JSON.
        return telemetryPublisher.publish(topic, IoTControlUnitClient.unquoteJasonString(payload));
    }

    private boolean handleWriteServiceRecord(Structure action) {
        if (action.getArity() != 5) {
            logger.warning("[ENV] writeServiceRecord expects (RID, Vehicle, Part, Tech, Cost), got " + action);
            return false;
        }
        String vehicleId = action.getTerm(1).toString();
        try {
            ServiceRecord record = logServiceRecordUseCase.execute(
                    intTerm(action.getTerm(0)),
                    vehicleId,
                    action.getTerm(2).toString(),
                    action.getTerm(3).toString(),
                    intTerm(action.getTerm(4)));
            logger.info("[ENV] Service record stored: " + record);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warning("[ENV] writeServiceRecord rejected: " + e.getMessage());
            return false;
        }
        // Unchanged from before: the coordinator is also told the booking was
        // confirmed (the service centre sends the same message itself).
        addPercept("fleet_coordinator_agent",
                ASSyntax.createLiteral("booking_confirmed", ASSyntax.createAtom(vehicleId)));
        return true;
    }

    private boolean handleEndorseServiceRecord(Structure action) {
        if (action.getArity() != 3) {
            logger.warning("[ENV] endorseServiceRecord expects (RID, Endorser, approve|reject), got " + action);
            return false;
        }
        String decision = action.getTerm(2).toString();
        if (!"approve".equals(decision) && !"reject".equals(decision)) {
            logger.warning("[ENV] endorseServiceRecord: decision must be approve or reject, got " + decision);
            return false;
        }
        try {
            ServiceRecordEndorsed event = endorseServiceRecordUseCase.execute(
                    intTerm(action.getTerm(0)), action.getTerm(1).toString(), "approve".equals(decision));
            logger.info("[ENV] " + event);
            return true;
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
            // unknown record, blank endorser, or a record that was already decided
            logger.warning("[ENV] endorseServiceRecord rejected: " + e.getMessage());
            return false;
        }
    }

    /** Reads a Jason number term (e.g. a record id or a cost) as an int. */
    private static int intTerm(Term term) {
        if (!term.isNumeric()) {
            throw new IllegalArgumentException("expected a number, got " + term);
        }
        try {
            return (int) ((NumberTerm) term).solve();
        } catch (Exception e) {
            throw new IllegalArgumentException("expected a number, got " + term, e);
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
