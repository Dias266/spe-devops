package application.usecase;

import application.port.BlockchainPort;
import application.port.ServiceRecordRepository;
import domain.model.ServiceRecord;

/**
 * Logs the service record a service centre creates when it accepts a booking:
 * builds the {@link ServiceRecord} aggregate (which validates every field),
 * stores it in the repository so it can be endorsed later, and hands it to the
 * ledger through {@link BlockchainPort}.
 *
 * Called from the {@code writeServiceRecord(RID, Vehicle, Part, Tech, Cost)}
 * action in service_center_agent.asl.
 */
public final class LogServiceRecord {

    private final ServiceRecordRepository repository;
    private final BlockchainPort blockchainPort;

    public LogServiceRecord(ServiceRecordRepository repository, BlockchainPort blockchainPort) {
        this.repository = repository;
        this.blockchainPort = blockchainPort;
    }

    /**
     * @return the stored record
     * @throws IllegalArgumentException if a field is invalid (see ServiceRecord)
     * @throws IllegalStateException    if a record with this id already exists
     */
    public ServiceRecord execute(int recordId, String vehicleId, String part, String technician, int cost) {
        if (repository.findById(recordId).isPresent()) {
            throw new IllegalStateException("Service record #" + recordId + " already exists");
        }
        ServiceRecord record = new ServiceRecord(recordId, vehicleId, part, technician, cost);
        repository.save(record);
        blockchainPort.writeServiceRecord(record);
        return record;
    }
}
