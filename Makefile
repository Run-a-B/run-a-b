SHELL := /bin/bash

ROOT_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ENV_FILE := $(ROOT_DIR)/.env
FRONTEND_DIR := $(ROOT_DIR)/apps/run-a-b-fe
API_DIR := $(ROOT_DIR)/api
GRADLE_HOME := $(API_DIR)/.gradle

.PHONY: setup env frontend-install frontend-deps backend-build db-up db-down dev check

setup: env frontend-install db-up backend-build

env:
	@if [ ! -f "$(ENV_FILE)" ]; then \
		cp "$(ROOT_DIR)/.env.example" "$(ENV_FILE)"; \
		echo "Created .env from .env.example"; \
	fi

frontend-install:
	cd "$(FRONTEND_DIR)" && npm ci

frontend-deps:
	@if [ ! -d "$(FRONTEND_DIR)/node_modules" ]; then \
		$(MAKE) frontend-install; \
	fi

backend-build:
	cd "$(API_DIR)" && GRADLE_USER_HOME="$(GRADLE_HOME)" ./gradlew classes

db-up: env
	docker compose --env-file "$(ENV_FILE)" up -d --wait mysql

db-down:
	docker compose --env-file "$(ENV_FILE)" down --remove-orphans

dev: env db-up frontend-deps
	@set -a; source "$(ENV_FILE)"; set +a; \
	(cd "$(API_DIR)" && GRADLE_USER_HOME="$(GRADLE_HOME)" ./gradlew bootRun) & api_pid=$$!; \
	(cd "$(FRONTEND_DIR)" && npm run dev -- --host 127.0.0.1) & frontend_pid=$$!; \
	cleanup() { kill $$api_pid $$frontend_pid 2>/dev/null || true; }; \
	trap cleanup EXIT; \
	trap 'exit 130' INT TERM; \
	while kill -0 $$api_pid 2>/dev/null && kill -0 $$frontend_pid 2>/dev/null; do sleep 1; done; \
	status=0; \
	if ! kill -0 $$api_pid 2>/dev/null; then wait $$api_pid || status=$$?; else wait $$frontend_pid || status=$$?; fi; \
	if [ $$status -eq 0 ]; then status=1; fi; \
	exit $$status

check: frontend-deps
	cd "$(API_DIR)" && GRADLE_USER_HOME="$(GRADLE_HOME)" ./gradlew test
	cd "$(FRONTEND_DIR)" && npm test
	cd "$(FRONTEND_DIR)" && npm run lint
	cd "$(FRONTEND_DIR)" && npm run build
