package application.usecase;

import application.port.BlockchainPort;
import application.port.ServiceRecordRepository;
import domain.event.FleetAnomalyDetected;
import domain.event.ServiceRecordEndorsed;
import domain.model.ServiceRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogServiceRecordTest {

    static class InMemoryRepo implements ServiceRecordRepository {
        final Map<Integer, ServiceRecord> store = new HashMap<>();

        @Override
        public void save(ServiceRecord record) {
            store.put(record.id(), record);
        }

        @Override
        public Optional<ServiceRecord> findById(int id) {
            return Optional.ofNullable(store.get(id));
        }
    }

    static class RecordingLedger implements BlockchainPort {
        final List<ServiceRecord> written = new ArrayList<>();

        @Override
        public boolean logFleetAnomaly(FleetAnomalyDetected event) {
            return true;
        }

        @Override
        public boolean writeServiceRecord(ServiceRecord record) {
            written.add(record);
            return true;
        }

        @Override
        public String readDigitalTwin(String vehicleId) {
            return "stub";
        }
    }

    private final InMemoryRepo repo = new InMemoryRepo();
    private final RecordingLedger ledger = new RecordingLedger();
    private final LogServiceRecord useCase = new LogServiceRecord(repo, ledger);

    @Test
    void storesTheRecordAndHandsItToTheLedger() {
        ServiceRecord record = useCase.execute(1, "vehicle_agent1", "oil_filter", "tech_a", 85);

        assertEquals(record, repo.findById(1).orElseThrow());
        assertFalse(record.isDecided());
        assertEquals(List.of(record), ledger.written);
    }

    @Test
    void refusesADuplicateRecordId() {
        useCase.execute(1, "vehicle_agent1", "oil_filter", "tech_a", 85);

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(1, "vehicle_agent2", "brake_pad", "tech_a", 140));
        assertEquals("vehicle_agent1", repo.findById(1).orElseThrow().vehicleId());
        assertEquals(1, ledger.written.size());
    }

    @Test
    void refusesInvalidFieldsAndStoresNothing() {
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(2, "vehicle_agent1", "oil_filter", " ", 85));
        assertTrue(repo.store.isEmpty());
        assertTrue(ledger.written.isEmpty());
    }

    @Test
    void loggedRecordCanThenBeEndorsedOnce() {
        useCase.execute(3, "vehicle_agent1", "brake_pad", "tech_a", 140);
        EndorseServiceRecord endorse = new EndorseServiceRecord(repo);

        ServiceRecordEndorsed event = endorse.execute(3, "fleet_coordinator_agent", true);

        assertTrue(event.isApproved());
        assertTrue(repo.findById(3).orElseThrow().isDecided());
        assertThrows(IllegalStateException.class, () -> endorse.execute(3, "fleet_coordinator_agent", false));
    }
}
