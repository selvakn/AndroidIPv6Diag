# Root Makefile — orchestrates both components and Docker Compose

.PHONY: help server-build server-run android-build \
        up up-server down logs test-server apk-install clean

help:
	@echo ""
	@echo "  IPv6 Diagnostic Tool — Local Development"
	@echo ""
	@echo "  Server (Go):"
	@echo "    make server-build    Build static binary → server/bin/server"
	@echo "    make server-run      Build and run server locally (HTTP-only)"
	@echo ""
	@echo "  Android:"
	@echo "    make android-build   Build debug APK → android/app/build/outputs/apk/debug/"
	@echo ""
	@echo "  Docker Compose:"
	@echo "    make up              Start server + Android emulator"
	@echo "    make up-server       Start server only (no emulator)"
	@echo "    make down            Stop all containers"
	@echo "    make logs            Tail all container logs"
	@echo "    make test-server     Run curl tests against the server container"
	@echo "    make apk-install     Install APK into running emulator"
	@echo ""
	@echo "  Browser UI:  http://localhost:6080  (Android emulator noVNC)"
	@echo "  Server HTTP: http://localhost:8080/diag"
	@echo ""

# ── Server ────────────────────────────────────────────────────────────────────
server-build:
	$(MAKE) -C server build

server-run:
	$(MAKE) -C server run

# ── Android ───────────────────────────────────────────────────────────────────
# Builds the debug APK. Uses the local Android SDK if ANDROID_HOME is set,
# otherwise falls back to a Docker container that provides the SDK.
ANDROID_SDK_IMAGE ?= thyrlian/android-sdk:latest

android-build:
	@if [ -n "$$ANDROID_HOME" ] || [ -d "$$HOME/Android/Sdk" ]; then \
		echo "Building with local Android SDK (Java managed by mise)..."; \
		cd android && mise exec -- ./gradlew assembleDebug; \
	else \
		echo "ANDROID_HOME not set — building inside Docker (downloads SDK on first run)..."; \
		docker run --rm \
			-v $(CURDIR)/android:/project \
			-v android-gradle-cache:/root/.gradle \
			-w /project \
			$(ANDROID_SDK_IMAGE) \
			bash -c "./gradlew assembleDebug"; \
	fi

# ── Docker Compose ────────────────────────────────────────────────────────────
up:
	docker compose up -d server android
	@echo ""
	@echo "  Server:  http://localhost:8080/diag"
	@echo "  Android: http://localhost:6080  (emulator takes ~2 min to boot)"
	@echo ""
	@echo "  To install APK after android-build:  make apk-install"

up-server:
	docker compose up -d server
	@echo ""
	@echo "  Server: http://localhost:8080/diag"
	@echo "  Run tests: make test-server"

down:
	docker compose down

logs:
	docker compose logs -f

test-server:
	docker compose run --rm test-server

apk-install: android-build
	docker compose --profile install run --rm apk-install

clean:
	docker compose down -v --remove-orphans
	$(MAKE) -C server clean
