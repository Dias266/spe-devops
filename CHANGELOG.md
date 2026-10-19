# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- MQTT integration test for the IoT control unit (`test/mqtt.test.js`, in-process aedes broker): HTTP publish reaches `vehicle/telemetry`, incoming telemetry is stored, and `WARNING`/`CRITICAL` health states are published on `vehicle/state`.
- `TelemetryPublisherPort` and `IoTControlUnitClient` for the MAS-to-IoT connection, with a JUnit contract test against a fake control unit.
- CircleCI delivery job: MQTT round trip through the real Mosquitto container after the smoke test.
- `LogServiceRecord` use case: the service centre's `writeServiceRecord` action now stores a real `ServiceRecord` in the repository and passes it to `BlockchainPort`.
- `endorseServiceRecord` environment action: the service centre's endorsement reply now goes through `EndorseServiceRecord`, so a record can be decided only once at runtime too.
- PostgreSQL persistence for service records (`PostgresServiceRecordRepository`, `deploy/postgres/schema.sql`); the database also refuses to change a decided record. Integration tests run against a PostgreSQL container in CircleCI.
### Changed
- `writeServiceRecord` takes `(RecordId, Vehicle, Part, Tech, Cost)` instead of `(Vehicle, Details)`; `service_center_agent.asl` updated.
- `FleetMASEnvironment` sends `sendMQTTMessage` through `TelemetryPublisherPort` instead of building the HTTP request itself; the request now times out after 5 s.

## [0.2.0] - 2026-10-17

### Added
- `Makefile` as one entry point: `make check` runs version check, lint and tests on all platforms; `make up` starts the stack.
- Checkstyle for the Java multi-agent system, run by `./gradlew check`.
- ESLint for the IoT control unit.
- Node.js API tests (start the real server, no Arduino or broker needed) and c8 coverage with a JUnit report in CircleCI.
- Dockerfiles for the multi-agent system, ML service and IoT control unit.
- Delivery job: builds every image, smoke-tests the whole stack with docker compose, then pushes multi-arch images (linux/amd64, linux/arm64) to ghcr.io on `main` and release tags.
- `docker-compose.yml` that runs the published images, with Mosquitto and a shared volume for ML predictions.
- Graceful shutdown on SIGTERM for the IoT control unit.

### Changed
- Service addresses are configurable by environment variable (`IOT_BACKEND_URL`, `AGENT_BASE_URL`, `PREDICTIONS_LOG`, `DASHBOARD_LOG`, `SERIAL_PORT`, `HTTP_PORT`) instead of hard-coded IPs.
- Dashboard backend uses `npm ci` with a lockfile and runs as a non-root user.

### Fixed
- IoT control unit never opened the Arduino serial port: the code referenced an undefined `data` variable (found by ESLint).
- Dashboard `/api/fleet` hung forever when agents were unreachable; requests now time out after 3 s.

## [0.1.0] - 2026-10-17

First tagged version of the integrated Vehicle Fleet Predictive Maintenance
project for Software Process Engineering.

### Added
- CircleCI pipeline with one job per target platform: JVM (Gradle), Python (pip) and Node.js (npm).
- JaCoCo coverage for the Java multi-agent system.
- pytest suite for the ML service, including an end-to-end Weka prediction test.
- Node.js test suite for the IoT control unit.
- flake8 and black linting for Python.
- MIT licence, `VERSION` file, changelog and contribution guide.
- `scripts/check-version.sh`, run in CI, so all components and release tags share one version.

### Changed
- Multi-Agent-System refactored into DDD / Clean Architecture layers (domain, application, infrastructure).
- All components reset to version 0.1.0, since the public API is not stable yet.

### Fixed
- Node.js manifest renamed from `PACKAGE.JSON` to `package.json` (Linux is case-sensitive).

### Removed
- Committed `node_modules`, `.DS_Store` and IDE cache files.

[Unreleased]: https://github.com/Dias266/spe-devops/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Dias266/spe-devops/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Dias266/spe-devops/releases/tag/v0.1.0
