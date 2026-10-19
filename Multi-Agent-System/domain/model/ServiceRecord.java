package domain.model;

import domain.event.ServiceRecordEndorsed;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root: an immutable, monotonically-ordered service record for a
 * single maintenance job, as logged by a ServiceCenterAgent.
 *
 * Mirrors service_record(RID, VehicleID, Part, Tech, Cost) in
 * mas/agents/service_center_agent.asl, plus the endorsement lifecycle
 * described in docs/message_protocol.md:
 *
 *   ServiceCenter -> Coordinator : endorse_request(RID, VehicleID)
 *   Coordinator   -> ServiceCenter: endorsement(RID, approve|reject)
 *
 * The invariant this class enforces — and which does not exist anywhere in
 * the current .asl/.java code — is that a record can only be decided once.
 * The .asl belief base has no such guard: nothing stops a second
 * +endorsement(RID, _) from firing and silently overwriting the outcome.
 * Here, a second call to endorse() throws instead of being allowed to
 * happen.
 */
public final class ServiceRecord {

    private final int id;
    private final String vehicleId;
    private final String part;
    private final String technician;
    private final int cost;

    /** Null until endorse() is called for the first time. */
    private Endorsement endorsement;

    public ServiceRecord(int id, String vehicleId, String part, String technician, int cost) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive (got " + id + ")");
        }
        this.id = id;
        this.vehicleId = requireNonBlank(vehicleId, "vehicleId");
        this.part = requireNonBlank(part, "part");
        this.technician = requireNonBlank(technician, "technician");
        if (cost < 0) {
            throw new IllegalArgumentException("cost must not be negative (got " + cost + ")");
        }
        this.cost = cost;
        this.endorsement = null;
    }

    /**
     * Records the outcome of a cross-organisation endorsement decision.
     *
     * @throws IllegalStateException if this record has already been decided —
     *         a service record is endorsed (or rejected) exactly once, then
     *         immutable, matching the "committed to ledger" language in the
     *         ServiceCenterAgent's own log output.
     */
    public ServiceRecordEndorsed endorse(String endorserId, boolean approved) {
        if (this.endorsement != null) {
            throw new IllegalStateException(
                    "Service record #" + id + " was already "
                            + (this.endorsement.isApproved() ? "endorsed" : "rejected")
                            + " by " + this.endorsement.endorserId()
                            + " — a committed record cannot be re-endorsed");
        }
        this.endorsement = Endorsement.decide(endorserId, approved);
        return new ServiceRecordEndorsed(id, vehicleId, approved);
    }

    public boolean isDecided() {
        return endorsement != null;
    }

    public Optional<Endorsement> endorsement() {
        return Optional.ofNullable(endorsement);
    }

    public int id() {
        return id;
    }

    public String vehicleId() {
        return vehicleId;
    }

    public String part() {
        return part;
    }

    public String technician() {
        return technician;
    }

    public int cost() {
        return cost;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
/** Rebuilds a record loaded from storage, with its endorsement if it was decided. */
    public static ServiceRecord restore(int id, String vehicleId, String part, String technician,
                                        int cost, Endorsement endorsement) {
        ServiceRecord record = new ServiceRecord(id, vehicleId, part, technician, cost);
        record.endorsement = endorsement;   // may be null: not decided yet
        return record;
    }


    @Override
    public boolean equals(Object o) {
        // Aggregates are compared by identity (id), not by field equality.
        if (this == o) return true;
        if (!(o instanceof ServiceRecord)) return false;
        ServiceRecord that = (ServiceRecord) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ServiceRecord{id=" + id + ", vehicleId='" + vehicleId + "', part='" + part
                + "', technician='" + technician + "', cost=" + cost
                + ", endorsement=" + endorsement + '}';
    }
}
