# Distributed - Fleet Operational Center

A distributed multi-node server environment built to orchestrate and monitor real-time network states. This system incorporates a Dockerized Node.js backend alongside a highly optimized, visual dashboard frontend featuring real-time telemetry elements like interactive SVG gauge animations, dynamic temperature rows, and granular signal-bar urgency indicators.

---

## 🚀 Features

* **Distributed Architecture:** Engineered for scalable, multi-node communication.
* **Real-Time Fleet UI:** Single-page tracking panel (`fleet_operational_center.html`) to visualize status across endpoints.
* **Dynamic Telemetry:** Custom CSS-animated SVG gauges (`.gauge-wrap`, `.gauge-arc`) displaying cool, warm, and critical states.
* **Urgency Indicators:** Custom responsive signal bars (`.signal`, `.signal.bars`) indicating immediate network priority.
* **Production Ready:** Pre-configured Docker environment for instant containerized deployment.

---

## 📁 Repository Structure

```text
├── backend/
│   ├── Dockerfile          # Container build recipes for the server
│   ├── package.json        # Backend dependencies & boot scripts
│   └── server.js           # Core Node.js server engine
├── frontend/
│   ├── index.html          # Core template dashboard
│   └── fleet_operational_center.html  # Fleet dashboard management console
├── docker-compose.yml      # Orchestration config for swift multi-container spin-ups
├── package.json            # Root configuration management
├── package-lock.json       # Dependency tree lockfile
└── README.md               # Project documentation
```

---

## 🛠️ Quick Start

### Prerequisites
* Ensure you have [Docker](https://docker.com) and [Docker Compose](https://google.com) installed.

### 1. Clone the Project
```bash
git clone https://github.com
cd DistributedCodenamesGame
```

### 2. Launch with Docker Compose
Spin up the entire node network and backend environment instantly:
```bash
docker-compose up --build
```

### 3. Access the Dashboards
Once container initialization completes, open your web browser to view your live fleet tracking metrics:
* **Primary Console:** `http://localhost:3000` *(or your configured backend port)*
* **Fleet Control:** `http://localhost:3000/frontend/fleet_operational_center.html`

---

<img width="2842" height="1354" alt="image" src="https://github.com/user-attachments/assets/12fb983d-c8ba-41bf-b7d7-cebd7d11b9a3" />


## 🎛️ UI & Styling Customization

The dashboard relies on lightweight CSS variables (`var(--accent)`, `var(--warn)`, `var(--crit)`) mapped onto native elements for low overhead:
* **Typography:** Built using the ultra-readable `IBM Plex Mono` monospace font for structural telemetry readability.
* **Gauges:** SVG strokes use transitions (`transition: stroke .4s ease`) to smoothly glide between network performance spikes without triggering page-wide repaints.

---

## ⚙️ Build, test and run

Everything goes through the `Makefile` (needs Java 21, Python 3.11, Node.js 20+ and Docker):

```bash
make install   # install dependencies for JVM, Python and Node.js
make check     # version check, lint and tests with coverage - same as CI
make up        # build and start the whole stack with docker compose
```

Or run the published images without building: `docker compose pull && docker compose up -d`.

| Service | Port | Image |
| --- | --- | --- |
| IoT control unit (Node.js) | 3002 | `ghcr.io/dias266/spe-iot-control-unit` |
| Multi-agent system (Java/Jason) | - | `ghcr.io/dias266/spe-mas` |
| ML prediction (Python/Weka) | - | `ghcr.io/dias266/spe-ml-service` |
| Dashboard backend | 3000 | `ghcr.io/dias266/spe-dashboard-backend` |
| Dashboard frontend | 8080 | `ghcr.io/dias266/spe-dashboard-frontend` |
| MQTT broker (Mosquitto) | 1883 | `eclipse-mosquitto:2` |
| Node-RED | 1880 | `nodered/node-red:4.0` |

CI/CD runs on CircleCI: see [`.circleci/config.yml`](.circleci/config.yml).

---

## 🤝 Contributing

We use trunk-based development with a rebasing policy (linear history on `main`),
Conventional Commits and Semantic Versioning. See [CONTRIBUTING.md](CONTRIBUTING.md)
for branch names, the pull request rules and how to cut a release.

Current version: see [VERSION](VERSION) and [CHANGELOG.md](CHANGELOG.md).

---

## 📄 License

Released under the [MIT License](LICENSE).
