.PHONY: release-hygiene release-history-hygiene deploy-preflight compose-config up down verify acceptance verify-server verify-web build build-server build-web test-server install-web clean

release-hygiene:
	python3 scripts/release-hygiene.py

release-history-hygiene:
	python3 scripts/history-hygiene.py

deploy-preflight:
	python3 scripts/deploy-preflight.py deploy/orbisops.env

compose-config: deploy-preflight
	docker compose --env-file deploy/orbisops.env config >/dev/null

up: deploy-preflight
	docker compose --env-file deploy/orbisops.env up -d --build

down:
	docker compose --env-file deploy/orbisops.env down

verify: release-hygiene verify-server verify-web

acceptance: verify
	cd web && npm run test:e2e

verify-server:
	cd server && mvn -B test

verify-web: install-web
	cd web && npm run verify

build: build-server build-web

build-server:
	cd server && mvn -B -DskipTests package

build-web: install-web
	cd web && npm run build

test-server:
	cd server && mvn -B test

install-web:
	cd web && npm ci

clean:
	cd server && mvn clean
	rm -rf web/node_modules web/dist web/.rsbuild web/coverage
