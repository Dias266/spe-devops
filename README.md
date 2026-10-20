# Vehicle Fleet Predictive Maintenance — DDD & DevOps

Software Process Engineering project (University of Bologna, Codice B0011).

A fleet of vehicles, a service centre and a fleet coordinator — implemented as a Jason (BDI)
multi-agent system — predict and book maintenance, and keep endorsed service records.
Around the agents sit an IoT control unit that receives telemetry over MQTT, a Weka-based
maintenance prediction service, PostgreSQL for persistence, and a web dashboard.

The project applies **Domain-Driven Design** (bounded contexts, ubiquitous language, aggregates,
ports and adapters, Clean Architecture) and **DevOps** (versioning, trunk-based development,
build automation, CI/CD, containerised multi-architecture delivery) to that system.

---

## 🧩 What is in the repository

| Folder | Bounded context | Platform |
| --- | --- | --- |
| [`Multi-Agent-System/`](Multi-Agent-System) | Fleet Coordination, Service & Booking (core), Trust & Service Ledger | JVM — Java 21, Jason, Gradle |
| [`Embedded-Systems-gs-main/`](Embedded-Systems-gs-main) | Vehicle Telemetry & Health — ESP32/Arduino sketches and the Node.js IoT control unit (MQTT) | Node.js, C/C++ |
| [`ML-model/`](ML-model) | Maintenance Prediction — Weka AdaBoostM1 model run from Python through JPype | Python |
| [`MAS_system_node_server_on-main/back/`](MAS_system_node_server_on-main/back) | Fleet dashboard — Express backend and static frontend | Node.js |
| [`deploy/`](deploy) | Mosquitto configuration and the PostgreSQL schema | — |
| [`with-red/`](with-red) | Node-RED flows | — |

### How the parts talk to each other

```text
ESP32 ──MQTT──► Mosquitto ◄──MQTT──► IoT control unit (Node.js) ◄──HTTP── Multi-agent system (Java/Jason)
                                                                              │
                                                                              ▼
ML service (Python + Weka) ──► PostgreSQL: maintenance_prediction      PostgreSQL: service_record
                                         │                                    │
                                         └──────────► Dashboard backend ◄─────┘ ──► Dashboard (browser)
```

- The **agents** store every accepted booking as a service record in PostgreSQL; the coordinator's
  endorsement is stored on it, and the database refuses to change a record that was already decided.
- The **ML service** predicts the maintenance need for each template instance and stores every
  prediction in PostgreSQL.
- The **dashboard** shows the live agent state plus two scrollable tables read from PostgreSQL:
  ML predictions and service records.

---

## 🛠️ Quick start

Needs [Docker](https://www.docker.com/) with Docker Compose.

```bash
git clone https://github.com/Dias266/spe-devops.git
cd spe-devops
docker compose up -d --build     # or: docker compose pull && docker compose up -d   (published images)
```

Then open the dashboard at **http://localhost:8080**. After about a minute the agents have made
their first bookings and the service records appear.

```bash
docker compose exec postgres psql -U fleet -d fleet -P pager=off -c "SELECT * FROM service_record;"
docker compose down -v           # stop everything; -v also deletes the database
```

Start each demo with `docker compose down -v`: the agents number service records from 1 on every
start, so a restart on old data refuses the first booking as a duplicate.

| Service | Port | Image |
| --- | --- | --- |
| Dashboard frontend | 8080 | `ghcr.io/dias266/spe-dashboard-frontend` |
| Dashboard backend | 3000 | `ghcr.io/dias266/spe-dashboard-backend` |
| IoT control unit (Node.js) | 3002 | `ghcr.io/dias266/spe-iot-control-unit` |
| Multi-agent system (Java/Jason) | - | `ghcr.io/dias266/spe-mas` |
| ML prediction (Python/Weka) | - | `ghcr.io/dias266/spe-ml-service` |
| PostgreSQL | - | `postgres:16` |
| MQTT broker (Mosquitto) | 1883 | `eclipse-mosquitto:2` |
| Node-RED | 1880 | `nodered/node-red:4.0` |

Images are published for `linux/amd64` and `linux/arm64`.

---

## ⚙️ Build, test and run

Everything goes through the `Makefile` (needs Java 21, Python 3.11, Node.js 20+ and Docker):

```bash
make install   # install dependencies for JVM, Python and Node.js
make check     # version check, lint and tests with coverage - same as CI
make up        # build and start the whole stack with docker compose
make help      # list all targets
```

Or run the published images without building: `docker compose pull && docker compose up -d`.

| Service | Port | Image |
| --- | --- | --- |
| PostgreSQL | - | postgres:16
| IoT control unit (Node.js) | 3002 | `ghcr.io/dias266/spe-iot-control-unit` |
| Multi-agent system (Java/Jason) | - | `ghcr.io/dias266/spe-mas` |
| ML prediction (Python/Weka) | - | `ghcr.io/dias266/spe-ml-service` |
| Dashboard backend | 3000 | `ghcr.io/dias266/spe-dashboard-backend` |
| Dashboard frontend | 8080 | `ghcr.io/dias266/spe-dashboard-frontend` |
| MQTT broker (Mosquitto) | 1883 | `eclipse-mosquitto:2` |
| Node-RED | 1880 | `nodered/node-red:4.0` |

CI/CD runs on CircleCI: see [`.circleci/config.yml`](.circleci/config.yml).

The MQTT path is tested at three levels: `Embedded-Systems-gs-main/test/mqtt.test.js` (control unit
against an in-process broker), `IoTControlUnitClientTest` (the Java MAS side of the same HTTP
contract), and an MQTT round trip through the real Mosquitto container in the delivery job.

---

## 🔁 CI/CD

CircleCI, configured in [`.circleci/config.yml`](.circleci/config.yml):

- **Every push and pull request:** version check, then lint, tests and coverage for the JVM
  (with a PostgreSQL container), Python and Node.js, in parallel.
- **`main` and release tags:** build all images, smoke-test the whole stack with Docker Compose
  (APIs, ML predictions and service records in PostgreSQL, MQTT round trip through Mosquitto),
  then publish multi-architecture images to the GitHub Container Registry.

---

## 📐 Design

- Agent design and the inter-agent message protocol:
  [`Multi-Agent-System/docs/`](Multi-Agent-System/docs)
- Clean Architecture layers of the agent system: `domain/`, `application/`, `infrastructure/`, `env/`
  in [`Multi-Agent-System/`](Multi-Agent-System)

---

## 🤝 Contributing

We use trunk-based development with a rebasing policy (linear history on `main`),
Conventional Commits and Semantic Versioning. See [CONTRIBUTING.md](CONTRIBUTING.md)
for branch names, the pull request rules and how to cut a release.

Current version: see [VERSION](VERSION) and [CHANGELOG.md](CHANGELOG.md).

---

## 📄 License

Released under the [MIT License](LICENSE).
