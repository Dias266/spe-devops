# Domain-Driven Design

This folder holds the domain analysis of the Vehicle Fleet Predictive Maintenance system,
in the order it was produced. Each step builds on the one before.

| # | Document | Question it answers |
| --- | --- | --- |
| 1 | [Knowledge crunching](01-knowledge-crunching.md) | What happens in the domain, and what don't we understand yet? |
| 2 | [Ubiquitous language](02-ubiquitous-language.md) | Which words do we use, and what exactly do they mean? |
| 3 | [Use cases](03-use-cases.md) | What must the system do, for whom? |
| 4 | [Bounded contexts and context map](04-bounded-contexts.md) | Where are the model boundaries, and how do the parts relate? |
| 5 | [Tactical model](05-tactical-model.md) | Which aggregates, entities, value objects, events and repositories make up the core contexts? |

## Sources

The analysis starts from the existing multi-agent system, which already encodes a lot of domain
knowledge:

- [`Multi-Agent-System/docs/agent_design.md`](../../Multi-Agent-System/docs/agent_design.md): agent beliefs, desires and plans
- [`Multi-Agent-System/docs/message_protocol.md`](../../Multi-Agent-System/docs/message_protocol.md): the 14 inter-agent messages (most are domain events)
- [`Multi-Agent-System/mas/agents/*.asl`](../../Multi-Agent-System/mas/agents): the agents themselves
- [`Multi-Agent-System/mas/common/shared_beliefs.asl`](../../Multi-Agent-System/mas/common/shared_beliefs.asl): the shared vocabulary
- the project proposal (bounded-context candidates, coarse requirements, ubiquitous-language seed)

## Status

Drafted from the sources above and reviewed against the code on `main`. Open questions are marked
**❓** throughout; each one is a decision the team still has to take.

Two markers say how far each part of the model is built:

| Marker | Meaning |
| --- | --- |
| ✅ | **Implemented** on `main` and covered by tests or a recorded run |
| 🎯 | **Target design**: modelled here, not yet in the code (the agents still take these decisions in AgentSpeak) |

## Where the model lives in the code

| Bounded context | Code on `main` | Storage |
| --- | --- | --- |
| Fleet Coordination | `fleet_coordinator_agent.asl`, `domain/model/BookingPressure.java`, `domain/event/FleetAnomalyDetected.java`, `LogFleetAnomaly` | — |
| Service & Booking | `service_center_agent.asl` | — |
| Vehicle Telemetry & Health | `vehicle_agent.asl`, `Embedded-Systems-gs-main/` (ESP32, Arduino, `control-unit-iot.js`) | in memory (last 100 readings) |
| Maintenance Prediction | `ML-model/main.py` (Weka through JPype) | PostgreSQL `maintenance_prediction` |
| Trust & Service Ledger | `ServiceRecord`, `Endorsement`, `LogServiceRecord`, `EndorseServiceRecord`, `ServiceRecordRepository` → `PostgresServiceRecordRepository` | PostgreSQL `service_record` |

The fleet dashboard (`MAS_system_node_server_on-main/back/`) is a read model over both PostgreSQL
tables and the agents' state.
