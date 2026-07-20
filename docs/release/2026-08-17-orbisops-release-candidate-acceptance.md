# OrbisOps Release Candidate Acceptance — 2026-08-17

## Decision

**LOCAL RELEASE CANDIDATE: GO**

The repository is ready for a public release candidate from a local build, packaging, migration, security-gate, browser-acceptance, and clean-clone perspective.

This conclusion does **not** claim successful live integration with external messaging providers that were not supplied with real platform credentials/callback endpoints. Those remain `SKIP / BLOCKED_EXTERNAL` until an actual provider smoke can be run.

## Release-candidate fixes discovered during cold-start validation

The cold-start exercise found and fixed issues that normal in-place development tests did not expose:

1. `server/config/orbisops.env.example` was missing `MYSQL_ROOT_PASSWORD`, even though the dependency Compose stack requires it.
2. The documented migration command relied on an executable bit that a clean checkout did not provide.
3. `scripts/db-migrate.sh` used an empty Bash array expansion that fails on the macOS system Bash 3.2 under `set -u`.
4. The disabled Repair adapter still initialized production filesystem paths during Spring startup.
5. The server Docker image did not package the bundled MCP scripts.
6. The server Docker image did not contain the `git`, `mysql`, or `psql` clients required by bundled Git/MySQL/PostgreSQL MCP adapters.
7. The non-root server container did not have a guaranteed writable Repair path.
8. GitHub Web CI did not execute the Chrome product-acceptance suite.
9. GitHub CI did not build and smoke-test the published server/web container shapes.
10. Root README quick-start commands were not safe to execute sequentially from one working directory, and first-admin bootstrap was not explicit enough in the primary path.
11. Global project selection maintained competing URL/local state, exposing a project-switch race in Command Palette use. Project scope is now derived from accessible projects + URL + remembered fallback, with one navigation path.

Relevant RC commits:

- `34b8637` — `fix: harden production cold start`
- `9f60bc8` — `fix: package bundled mcp runtime`
- `7be9933` — `fix: stabilize project context switching`
- `3e02ec4` — `ci: enforce release candidate gates`

## Empty-database acceptance

A dedicated temporary dependency stack was created with isolated MySQL, PostgreSQL/pgvector, and Redis state. It did not reuse the normal development database.

Results:

- non-root `orbisops` MySQL account had authority on the target `orbisops` schema;
- migration manifest entries `001` through `074` all applied successfully to the empty database;
- a second migration run completed successfully with no duplicate application, proving the normal runner path is idempotent;
- migration SHA-256 verification remained active;
- the temporary RC containers, network, and dedicated named volumes were removed after validation.

The migration runner was also syntax-checked after the macOS Bash portability fix.

## Production-profile cold start

The packaged server JAR was started with the `prod` Spring profile against the isolated dependency stack and explicit external configuration.

Observed authoritative startup evidence included:

```text
Tomcat started on port 18099
Started OrbisOpsApplication in 5.228 seconds
```

The validation command later terminated the process intentionally; Spring completed graceful shutdown. The application did not fail during startup.

### First administrator

First-run bootstrap was enabled only for the isolated RC startup.

Database verification showed that the temporary administrator record was:

- role: `admin`;
- enabled;
- stored with a BCrypt hash (`$2a$`, 60 characters);
- not stored with the supplied plaintext password.

`BootstrapAdminConfigurationTest` now locks the following behavior:

- disabled bootstrap does not touch the account store;
- an existing administrator is never overwritten;
- enabled bootstrap fails closed without an external credential;
- a first-run administrator is created through the normal account application use case.

No temporary RC administrator credential is recorded in this document or committed to the repository.

## Backend verification

On the final committed RC code before this acceptance document:

```text
server release hygiene: PASS
backend full Maven reactor: exit 0
Spring application context: PASS
Testcontainers MySQL: PASS
MCP stdio integration: PASS
ArchUnit / DDD architecture gates: PASS
```

A separate clean build was also run with Maven `clean package`, forcing build outputs to be regenerated instead of relying on existing `target` directories.

## Frontend verification

Final local workspace acceptance:

```text
release hygiene: PASS
TypeScript typecheck: PASS
Vitest: 118 / 118 PASS
production build: PASS
Chrome Playwright: 36 / 36 PASS
```

The Chrome suite includes mobile (`390px`), tablet (`820px`), desktop, project context, Channel setup, Incident/Change, Governance, accessibility, Command Palette, and other governed product paths.

The Command Palette project-switch regression was repeatedly exercised ten times after the ProjectScope fix: `10 / 10 PASS`.

The Command Palette browser test now also rejects unexpected `pageerror` events. The prior React `Maximum update depth exceeded` error did not recur after ProjectScope was converted to a single derived selection source. `ResizeObserver loop completed with undelivered notifications` remains browser/component-library observer noise and is not treated as an application exception.

## Container acceptance

### Server image

The server RC image was built successfully as a non-root runtime image.

Verified inside the image:

- runtime user is UID `10001` (`orbisops`);
- `/var/lib/orbisops/repair` is writable by that user;
- bundled MCP scripts exist under `/opt/orbisops/scripts/mcp/`;
- representative MySQL, PostgreSQL, and Git MCP scripts pass `node --check`;
- Node 20 runtime is available;
- Git client is available;
- MySQL client is available;
- PostgreSQL `psql` client is available.

The image build context now comes from `server/`, with a dedicated `.dockerignore`, so the JAR and bundled MCP runtime can both be packaged without accidentally sending the whole repository into the image build.

A full Spring Boot process was not started inside `docker run` with database credentials because the local connector safety layer rejected forwarding credential-like environment variables to that command. This is **not** reported as a container application-start PASS. Instead, production JAR startup and container runtime/package smoke are recorded separately and accurately.

### Web image

The web RC image was built successfully.

Verified:

```text
nginx -t: PASS
/usr/share/nginx/html/index.html: present
```

## Fresh-clone acceptance

A new local clone was created from the committed repository into a fresh temporary directory using `--no-hardlinks`.

Before any build, it was verified that the clone had:

- clean Git status;
- no `server/.env`;
- no `.ai-bridge` state;
- no server `target` output;
- no web `node_modules`.

From that fresh clone:

### Configuration

- repository release hygiene: PASS;
- official `server/config/orbisops.env.example` could be sourced after placeholder interpretation;
- `docker compose config`: PASS.

### Backend

- `mvn clean package` from scratch: PASS.

The full backend reactor had already passed on the exact same committed code in the primary workspace; the fresh clone clean package additionally proves no ignored/untracked build input is required.

### Web

A completely fresh install was performed:

```text
npm ci: PASS
Vitest: 118 / 118 PASS
typecheck: PASS
production build: PASS
npm audit --audit-level=high: 0 vulnerabilities
Chrome Playwright: 36 / 36 PASS
```

The npm install reported deprecation notices for indirect packages, but `npm audit --audit-level=high` reported zero vulnerabilities.

### Fresh-clone images

Both images were rebuilt from the fresh clone rather than from the original development workspace.

Server fresh-clone image smoke:

- non-root UID check: PASS;
- Repair writable path: PASS;
- bundled MCP script presence: PASS;
- Node MCP syntax check: PASS;
- Git/MySQL/PostgreSQL runtime clients: PASS.

Web fresh-clone image smoke:

- image build: PASS;
- `nginx -t`: PASS;
- generated `index.html`: PASS.

## GitHub release gates

CI now matches the local release-candidate standard more closely.

### Server CI

In addition to release hygiene and Maven tests, CI now:

- packages the server JAR;
- builds the server image;
- verifies non-root UID;
- verifies Repair path writability;
- verifies bundled MCP script presence/syntax;
- verifies Git/MySQL/PostgreSQL runtime clients.

### Web CI

CI now:

- performs `npm ci`;
- installs Playwright Chrome;
- runs frontend `verify`;
- runs the full Chrome product-acceptance suite;
- builds the web image;
- smoke-tests nginx configuration and static output.

Both workflow YAML files were parsed successfully during local RC validation.

## Remaining external acceptance

The following are intentionally **not** converted into local PASS results without real external platform conditions:

- Feishu / Lark real account and callback/connection smoke;
- WeCom real account smoke;
- DingTalk real account smoke;
- Slack real workspace smoke;
- Telegram real bot smoke;
- Discord real application/gateway smoke;
- QQ real bot smoke;
- WeChat Official Account real callback/API smoke.

For any provider without supplied credentials/network/callback setup, status remains:

```text
SKIP / BLOCKED_EXTERNAL
```

Contract tests, mock E2E, readiness logic, and protocol tests are not presented as substitutes for live platform integration.

## Release recommendation

The current repository is suitable to treat as a **local Release Candidate** and is ready for the next release step:

1. optionally run one or two real-provider smokes where credentials are available;
2. choose the public version/tag;
3. push the committed branch;
4. require GitHub Actions to pass on the remote repository;
5. create the release only after remote CI is green.

No Git push or release tag was performed as part of this acceptance.
