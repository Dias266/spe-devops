package infrastructure;

import domain.model.ServiceRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs against a real PostgreSQL; skipped when TEST_DATABASE_URL is not set. */
class PostgresServiceRecordRepositoryTest {

    private static final String URL = System.getenv("TEST_DATABASE_URL");
    private PostgresServiceRecordRepository repo;

    @BeforeEach
    void freshTable() throws Exception {
        assumeTrue(URL != null, "TEST_DATABASE_URL not set - skipping database tests");
        try (Connection c = DriverManager.getConnection(URL, "fleet", "fleet");
             Statement s = c.createStatement()) {
            s.execute(Files.readString(Path.of("../deploy/postgres/schema.sql")));
            s.execute("TRUNCATE service_record");
        }
        repo = new PostgresServiceRecordRepository(URL, "fleet", "fleet");
    }

    @Test
    void savesAndLoadsAnUndecidedRecord() {
        repo.save(new ServiceRecord(1, "vehicle_agent1", "oil_filter", "tech_a", 85));

        ServiceRecord loaded = repo.findById(1).orElseThrow();
        assertEquals("oil_filter", loaded.part());
        assertFalse(loaded.isDecided());
    }

    @Test
    void keepsTheEndorsement() {
        ServiceRecord record = new ServiceRecord(2, "vehicle_agent2", "brake_pad", "tech_a", 140);
        record.endorse("fleet_coordinator_agent", true);
        repo.save(record);

        ServiceRecord loaded = repo.findById(2).orElseThrow();
        assertTrue(loaded.isDecided());
        assertEquals("fleet_coordinator_agent", loaded.endorsement().orElseThrow().endorserId());
    }

    @Test
    void databaseRefusesToChangeADecidedRecord() {
        ServiceRecord first = new ServiceRecord(3, "vehicle_agent3", "oil_filter", "tech_b", 85);
        first.endorse("fleet_coordinator_agent", true);
        repo.save(first);

        ServiceRecord forged = new ServiceRecord(3, "vehicle_agent3", "oil_filter", "tech_b", 85);
        forged.endorse("someone_else", false);
        assertThrows(IllegalStateException.class, () -> repo.save(forged));
    }

    @Test
    void unknownIdIsEmpty() {
        assertTrue(repo.findById(999).isEmpty());
    }
}
