package infrastructure;

import application.port.ServiceRecordRepository;
import domain.model.Endorsement;
import domain.model.ServiceRecord;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;

/** Stores service records in PostgreSQL (table created by deploy/postgres/schema.sql). */
public final class PostgresServiceRecordRepository implements ServiceRecordRepository {

    private static final String UPSERT =
            "INSERT INTO service_record (id, vehicle_id, part, technician, cost, endorser_id, approved, decided_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (id) DO UPDATE SET endorser_id = EXCLUDED.endorser_id, "
            + "approved = EXCLUDED.approved, decided_at = EXCLUDED.decided_at";

    private static final String SELECT =
            "SELECT id, vehicle_id, part, technician, cost, endorser_id, approved, decided_at "
            + "FROM service_record WHERE id = ?";

    private final String url;
    private final String user;
    private final String password;

    public PostgresServiceRecordRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public void save(ServiceRecord record) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(UPSERT)) {
            ps.setInt(1, record.id());
            ps.setString(2, record.vehicleId());
            ps.setString(3, record.part());
            ps.setString(4, record.technician());
            ps.setInt(5, record.cost());
            Optional<Endorsement> e = record.endorsement();
            if (e.isPresent()) {
                ps.setString(6, e.get().endorserId());
                ps.setBoolean(7, e.get().isApproved());
                ps.setTimestamp(8, Timestamp.from(e.get().decidedAt()));
            } else {
                ps.setNull(6, Types.VARCHAR);
                ps.setNull(7, Types.BOOLEAN);
                ps.setNull(8, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save service record #" + record.id(), ex);
        }
    }

    @Override
    public Optional<ServiceRecord> findById(int id) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(SELECT)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp decidedAt = rs.getTimestamp("decided_at");
                Endorsement endorsement = decidedAt == null ? null
                        : new Endorsement(rs.getString("endorser_id"), rs.getBoolean("approved"), decidedAt.toInstant());
                return Optional.of(ServiceRecord.restore(rs.getInt("id"), rs.getString("vehicle_id"),
                        rs.getString("part"), rs.getString("technician"), rs.getInt("cost"), endorsement));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load service record #" + id, ex);
        }
    }
}
