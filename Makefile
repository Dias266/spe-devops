# One entry point for building, checking and running the whole system.
# Run `make help` to list the targets. CI runs the same commands per platform.

MAS := Multi-Agent-System
ML  := ML-model
IOT := Embedded-Systems-gs-main
BACKEND := MAS_system_node_server_on-main/back/backend
PYTHON ?= python3

.DEFAULT_GOAL := help
.PHONY: help install build lint test coverage check version-check \
        docker-build up down logs clean \
        lint-java lint-python lint-node test-java test-python test-node

help: ## List the available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  %-14s %s\n", $$1, $$2}'

install: ## Install dependencies for all three platforms
	cd $(IOT) && npm ci
	cd $(BACKEND) && npm ci
	cd $(ML) && $(PYTHON) -m pip install -r requirements.txt -r requirements-dev.txt
	cd $(MAS) && ./gradlew --no-daemon dependencies > /dev/null

build: ## Compile the JVM project and package its jar
	cd $(MAS) && ./gradlew --no-daemon jar copyRuntimeLibs

lint-java: ## Checkstyle
	cd $(MAS) && ./gradlew --no-daemon checkstyleMain checkstyleTest
lint-python: ## flake8 + black
	cd $(ML) && flake8 . --count --select=E9,F63,F7,F82 --statistics && black --check .
lint-node: ## ESLint
	cd $(IOT) && npm run lint
lint: lint-java lint-python lint-node ## Lint all platforms

test-java: ## JUnit + JaCoCo
	cd $(MAS) && ./gradlew --no-daemon test jacocoTestReport
test-python: ## pytest with coverage
	cd $(ML) && $(PYTHON) -m pytest --cov=. --cov-report=term --cov-report=html:htmlcov
test-node: ## node:test with c8 coverage
	cd $(IOT) && npm run test:ci
test: test-java test-python test-node ## Test all platforms (with coverage reports)
coverage: test ## Same as test; reports land in build/reports, htmlcov and coverage

version-check: ## Check every component carries the version in VERSION
	./scripts/check-version.sh

check: version-check lint test ## Everything CI checks on a pull request

docker-build: ## Build all service images locally
	docker compose build
up: ## Start the whole stack (builds images if needed)
	docker compose up -d --build
down: ## Stop the stack and remove its volumes
	docker compose down -v
logs: ## Follow the logs of all services
	docker compose logs -f

clean: ## Remove build outputs and reports
	cd $(MAS) && ./gradlew --no-daemon clean
	rm -rf $(ML)/htmlcov $(ML)/.coverage $(ML)/coverage.xml $(ML)/test-results
	rm -rf $(IOT)/coverage $(IOT)/reports
