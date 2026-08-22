# OrbisOps Server

Spring Boot backend for OrbisOps.

The root [`compose.yml`](../compose.yml) is the supported end-to-end deployment path. This document is for backend development and architecture work.

## Modules

```text
orbisops-domain
    framework-independent domain objects and policies

orbisops-application
    use cases, commands/queries, process managers and outbound ports

orbisops-infrastructure
    JDBC/MyBatis/Redis persistence and outbound infrastructure adapters

orbisops-api
    public transport DTOs and response contracts

orbisops-trigger
    REST/SSE, scheduling, channel, MCP and other inbound/technical adapters

orbisops-types
    small shared stable technical/value types

orbisops-app
    Spring Boot composition root
```

The intended dependency direction is:

```text
Domain ← Application ← Infrastructure / Trigger ← App
```

Architecture tests enforce the layer direction and bounded-context rules. Domain/application code must not depend on Spring MVC, controller DTOs, JDBC/MyBatis implementations, HTTP SDKs or JSON transport types.

## Requirements for local source development

- JDK 17
- Maven 3.9+
- Docker with Compose v2 for local infrastructure
- Node.js 20 only when exercising bundled local MCP adapters

## Start infrastructure

Prepare the repository deployment file from the root:

```bash
cp deploy/orbisops.env.example deploy/orbisops.env
```

Replace infrastructure secret placeholders, then validate it:

```bash
make deploy-preflight
```

Start only the data services:

```bash
docker compose --env-file deploy/orbisops.env up -d mysql pgvector redis
```

Run migrations through the same server image used by the full deployment:

```bash
docker compose --env-file deploy/orbisops.env run --rm migrate
```

This avoids a second migration configuration path. The migration manifest is `db/migrations/manifest.tsv`; applied versions/checksums are recorded in `orbisops_schema_history`.

## Build and test

```bash
mvn -B test
mvn -B -DskipTests package
```

From the repository root, `make verify` also runs the tracked-file release hygiene gate and frontend verification.

## Run from source

For day-to-day backend development, use the `dev` Spring profile and provide the database/model settings required by the feature you are exercising. The profile contains only local-development defaults; production deployment uses the root Compose configuration.

```bash
mvn -pl orbisops-app -am spring-boot:run
```

No developer-specific JDK/Maven path is required. Override endpoints and credentials with environment/Spring configuration instead of editing source files.

## Models

Model execution is an external capability. The server does not install or start an LLM, embedding service or reranker as part of production deployment.

A local model helper under `tools/` may be used for development experiments. It is optional and must not be treated as a runtime dependency.

The administration APIs/UI own model-provider catalog configuration. The OpenAI-compatible transport in the backend is an adapter protocol, not a requirement to use a particular vendor or model name.

## MCP adapters

Bundled local MCP process adapters are under `scripts/mcp/`. The server Docker image includes the scripts plus the runtime CLIs required by the Git/MySQL/PostgreSQL adapters.

Registering an MCP resource does not automatically authorize it. Project scope, credential resolution, tool discovery, capability binding, runtime authority and audit remain separate boundaries.

## Database schema

Never edit a migration that has already been released/applied. Add a new manifest entry and SQL file with its SHA-256 checksum.

The public Compose deployment executes migrations before backend health is considered ready.

## First administrator

There is no default user/password. The root deployment config provides a one-time bootstrap:

```text
ORBISOPS_BOOTSTRAP_ADMIN_ENABLED
ORBISOPS_BOOTSTRAP_ADMIN_USER_ID
ORBISOPS_BOOTSTRAP_ADMIN_USERNAME
ORBISOPS_BOOTSTRAP_ADMIN_PASSWORD
```

Bootstrap creates the account through the normal account application use case, which validates and hashes the credential. Disable bootstrap after the first successful startup. An existing username is never overwritten.

## Architecture documentation

- [`docs/architecture/strategic-ddd-context-map.md`](docs/architecture/strategic-ddd-context-map.md)
- [`docs/architecture/ddd-convergence-status.md`](docs/architecture/ddd-convergence-status.md)
- [`docs/architecture/adr/`](docs/architecture/adr/)

## License

Apache License 2.0. See [`../LICENSE`](../LICENSE).
