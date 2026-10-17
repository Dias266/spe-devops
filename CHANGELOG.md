# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/Dias266/spe-devops/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Dias266/spe-devops/releases/tag/v0.1.0
