# 2. Ubiquitous language

One word, one meaning, inside a bounded context. These terms are used the same way in
conversation, in documents and **in the code**: class, method and event names below are the
names the code uses (✅) or will use once the target design is built (🎯).

When a word means different things in different contexts, it is listed once per context
(see [§2.3](#23-same-word-different-meaning)).

## 2.1 Terms

| Term | Context | Definition | In code |
| --- | --- | --- | --- |
| **VIN** | all | Vehicle Identification Number: the 17-character identifier of one vehicle. The identity everything else refers to. | 🎯 `Vin` (value object); today a plain string `vehicleId` |
| **Digital twin** | Telemetry & Health | The system's up-to-date picture of one physical vehicle: its latest telemetry, health and registration. | 🎯 `VehicleTwin` (aggregate); today `readDigitalTwin` returns raw text |
| **Registration** | Telemetry & Health | Recording a VIN and its public key, so its telemetry can be authenticated. | ✅ `RegisterVehicle` (checks the VIN); 🎯 public key |
| **Telemetry record** | Telemetry & Health | One signed reading from a vehicle: VIN, temperature, mileage, state, timestamp, signature. | ✅ MQTT packet on `vehicle/telemetry`; 🎯 `TelemetryRecord` (value object) |
| **Authentication** | Telemetry & Health | Checking a telemetry record's ECDSA signature against the vehicle's registered key. An unauthenticated record is discarded. | ✅ Arduino ECDSA check (`AUTH_RESULT:VALID`); 🎯 `authenticate(record)` |
| **Anomaly** | Telemetry & Health | A reading outside its normal range for one vehicle, with a type (e.g. `brake_wear`) and a severity. | ✅ `IoTAnomaly`, percept `anomaly_detected/3`; 🎯 event `AnomalyDetected` |
| **Sampling rate** | Telemetry & Health | How often a vehicle reports: every 5 s below 30 °C, 2 s from 30 to 40 °C, 1 s at 40 °C and above. | ✅ ESP32 `FREQ_*`, agent `calculate_sampling_delay`; 🎯 `SamplingPolicy` |
| **Health assessment** | Telemetry & Health | The verdict on one vehicle's latest telemetry: its condition and its urgency. | 🎯 `HealthAssessment` (value object); today `urgency_level/1` in the vehicle agent |
| **Urgency** | all | How soon a vehicle needs service, on one ordered scale: `LOW < MEDIUM < HIGH < CRITICAL`. | 🎯 `Urgency` (enum); today Jason atoms `low`…`critical` |
| **Maintenance prediction** | Maintenance Prediction | The model's verdict on whether a vehicle needs maintenance, with a confidence. A prediction below 70 % confidence is treated as *no maintenance needed*. | ✅ `ML-model/main.py`, table `maintenance_prediction`; `MaintenanceInsight` / `MLPredictionPort` toward the agents |
| **Maintenance request** | Fleet Coordination | A vehicle's request for service: which vehicle, which part, how urgent. | ✅ message `book_request/3`; 🎯 `MaintenanceRequest`, event `MaintenanceRequested` |
| **Booking pressure** | Fleet Coordination | The fleet-wide load signal, `LOW < MEDIUM < HIGH < CRITICAL`. Each routed request raises it one level, each accepted booking lowers it one level, and it evaporates back toward `LOW` over time. Vehicles read it; nobody negotiates it. | ✅ `BookingPressure` (value object) and belief `booking_pressure/1` |
| **Evaporation** | Fleet Coordination | The gradual decay of booking pressure: one level every 4 clock ticks (about 8 s), like a pheromone trail fading. | ✅ `+tick` plan in the coordinator, `BookingPressure.decay()` |
| **Self-deferral** | Fleet Coordination | A vehicle that is not itself critical choosing not to request service while pressure is `CRITICAL`. Load shedding without a central decision. | ✅ `+!request_fleet_booking` in the vehicle agent; 🎯 policy `DeferUnderCriticalPressure` |
| **Fleet anomaly pattern** | Fleet Coordination | The same anomaly type reported by at least *threshold* vehicles (default 2). Triggers a collective alert. | ✅ event `FleetAnomalyDetected`; 🎯 renamed `FleetAnomalyPatternDetected` |
| **Routing** | Fleet Coordination | Choosing which service centre receives a maintenance request, based on booking pressure and each centre's advertised capacity. | 🎯 `FleetCoordinator.route(request)`; today one centre, forwarded directly |
| **Service centre** | Service & Booking | A workshop with a finite capacity, technicians, parts stock and a slot calendar. Decides on every booking request it receives. | ✅ `service_center_agent`; 🎯 `ServiceCenter` (aggregate) |
| **Capacity** | Service & Booking | The number of jobs a service centre can work on at the same time (free technician slots). | ✅ belief `current_capacity/1`; 🎯 `Capacity` (value object) |
| **Slot** | Service & Booking | A bookable time unit in a service centre's calendar; either free or occupied. | ✅ belief `available_slot/2`; 🎯 `Slot`, `SlotCalendar` |
| **Technician** | Service & Booking | A person who performs services, qualified for a set of parts. | ✅ belief `technician/1`; 🎯 `Technician` (entity) |
| **Qualification** | Service & Booking | A technician's ability to service a given part. No qualified technician means the booking is declined. | ✅ belief `qualified/2`; 🎯 `Technician.isQualifiedFor(part)` |
| **Part** | Service & Booking | A replaceable component, identified by a part number (e.g. `brake_pad`, `oil_filter`). | ✅ atoms `brake_pad`, `oil_filter`; 🎯 `PartId` (value object) |
| **Parts stock** | Service & Booking | The quantity of each part a service centre holds. | ✅ belief `parts_inventory/2`; 🎯 `PartsInventory` |
| **Booking** | Service & Booking | A service centre's commitment to service one vehicle: a slot, a technician and a part reserved for it. | 🎯 `Booking` (entity) |
| **Accept / Defer / Decline** | Service & Booking | The three possible decisions on a booking request. **Accept**: slot, part, capacity and qualified technician all available. **Defer**: no capacity or no free slot now; counter-offer a later slot. **Decline**: the part is out of stock or nobody is qualified. | ✅ messages `booking_confirmed/2`, `booking_deferred/2`, `booking_declined/1`; 🎯 events `BookingAccepted`, `BookingDeferred`, `BookingDeclined` |
| **Decline reason** | Service & Booking | Why a booking was declined: `PARTS_SHORTAGE`, `NO_QUALIFIED_TECHNICIAN`, `UNAVAILABLE`. | ✅ `parts_shortage(P)`, `no_qualified_technician(P)`, `unavailable`; 🎯 `DeclineReason` (enum) |
| **Service completed** | Service & Booking | The job on a booking is finished; its slot and capacity are released. | ✅ message `service_completed/2`; 🎯 event `ServiceCompleted` |
| **Service record** | Trust & Service Ledger | The permanent, ordered account of one completed service: vehicle, part, technician, cost. Once decided, it never changes. | ✅ `ServiceRecord` (aggregate), table `service_record` |
| **Endorsement** | Trust & Service Ledger | Another organisation's signed decision (approve or reject) on a service record. A record is decided exactly once. | ✅ `Endorsement` (value object), stored on the record |
| **Endorser** | Trust & Service Ledger | The organisation that endorses a record; must differ from the one that logged it. | ✅ `Endorsement.endorserId` |
| **Committed** | Trust & Service Ledger | A service record that has been endorsed with *approve*. Only committed records appear in service history. | ✅ `approved = true` on a decided record; 🎯 `ServiceRecord.isCommitted()` |
| **Integrity hold** | Trust & Service Ledger | A service record endorsed with *reject*: kept for audit but not part of service history. | ✅ `approved = false` on a decided record; 🎯 `ServiceRecord.isOnHold()` |
| **Service history** | Trust & Service Ledger | All committed service records of one VIN, in order. What stakeholders retrieve. | ✅ dashboard `GET /api/service-records` (all records, newest first); 🎯 `ServiceHistory` per VIN, committed only |
| **Stakeholder** | Trust & Service Ledger | Someone allowed to read service history: vehicle owner, service centre, fleet operator. | ❓ access rules |

## 2.2 Words we do **not** use

| Avoid | Use instead | Why |
| --- | --- | --- |
| "Booking confirmed" | **Booking accepted** (to the vehicle) / **Capacity consumed** (to the coordinator) | The old message `booking_confirmed` meant two different things (hot spot H2). |
| "Job", "ticket", "appointment" | **Booking** | One word for the service centre's commitment. |
| "Ledger entry", "transaction" | **Service record** | The record is the domain concept; the blockchain is only how it is stored. |
| "Alert" on its own | **Anomaly** (one vehicle) or **Fleet anomaly pattern** (several) | Different scope, different reaction. |
| "Score" | **Confidence** (prediction) or **Urgency** (health) | The ML score is not used directly by the rest of the domain. |
| "Urgency `high`/`low`" only | the full 4-level **Urgency** scale | Two scales existed (hot spot H5). |

## 2.3 Same word, different meaning

| Word | In Fleet Coordination | In Service & Booking | In Trust & Service Ledger |
| --- | --- | --- | --- |
| **Vehicle** | a member of the fleet that can request maintenance | the subject of a booking; only its VIN matters | the subject of a service history; only its VIN matters |
| **Capacity** | the capacity a centre *advertised* (may be stale) | the real, current number of free technician slots | ✅ belief `current_capacity/1`; 🎯 `Capacity` (value object) |
| **Request** | a maintenance request to be routed | a booking request to be decided | an endorsement request |

Each context keeps its own meaning; translation happens at the boundary (see the
[context map](04-bounded-contexts.md#42-context-map)).
