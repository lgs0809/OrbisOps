.PHONY: verify acceptance verify-server verify-web build build-server build-web test-server install-web clean

verify: verify-server verify-web

acceptance: verify
	cd web && npm run test:e2e

verify-server:
	cd server && python3 scripts/release-hygiene.py
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
