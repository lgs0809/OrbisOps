# OrbisOps

**Governed Agentic Operations Platform**

OrbisOps is an operations-focused Agent platform for investigating production systems, orchestrating repeatable operational workflows, and governing changes with explicit evidence, approval, execution, verification, and recovery boundaries.

It follows a simple runtime rule:

- **Open-ended work** uses the project default **ReAct Agent**.
- **Repeatable operational SOPs** use a published **Workflow** whose Agent nodes run in `DIRECT`, `LLM`, or `REACT` mode.
- **Capabilities** such as MCP servers, Skills, RAG, code access, and tools are bound to Agent nodes instead of becoming independent workflow node types.
- **Production mutation** is never obtained by self-escalation. A prepared `ChangePackage` must be approved before an independent trusted Landing runtime receives production authority.

## What OrbisOps provides

- Project-scoped default ReAct Agents and published specialized Workflows
- Workflow nodes for `START`, `AGENT`, `ROUTER`, `SUB_WORKFLOW`, and `END`
- `DIRECT`, `LLM`, and `REACT` Agent execution modes
- Bounded feedback loops and durable node-attempt checkpoints
- MCP discovery, schema governance, progressive tool disclosure, and remote-call audit evidence
- RAG, Skill packages, memory, channels, schedules, alerts, incidents, and execution history
- `OBSERVE_ONLY`, `PREPARE_CHANGE`, and trusted Landing authority boundaries
- `ChangePackage → Approval → Landing → Verification / Rollback`
- Durable Work Sessions that can recover after process loss without blindly re-running completed tool calls
- Audit records for model execution, tool execution, workflow state, authority, evidence, and change operations

## Runtime model

```text
Chat / Alert / Schedule / Channel / API
                 │
                 ▼
      Project entry policy
          ┌──────┴─────────┐
          │                │
          ▼                ▼
 Default ReAct       Published Workflow
                           │
          START → AGENT → ROUTER → ... → END
                    │
        DIRECT | LLM | REACT
                    │
     MCP / Skill / RAG / Tool / Code
                    │
             Evidence / Report
                    │
          optional ChangePackage
                    │
                 Approval
                    │
       trusted Landing ReAct runtime
                    │
          Verification / Rollback
```

A normal Workflow or ReAct run can inspect systems and, when explicitly authorized, prepare an isolated change. It cannot turn itself into a production-write runtime. Production authority belongs to a separate approved Landing execution.

## Authority model

| Authority | Intended use | Production mutation |
| --- | --- | --- |
| `OBSERVE_ONLY` | diagnosis, inspection, reporting | no |
| `PREPARE_CHANGE` | investigation, isolated repair, test/build, ChangePackage preparation | no direct governed production mutation |
| `PROD_FULL` | trusted approved Landing runtime only | yes, subject to Landing controls |

MCP/tool execution is also governed by executable, proposal-only, or hidden exposure. A callback returning a policy denial is not recorded as a successful remote execution; `remoteCallExecuted` is persisted separately from framework-level callback completion.

## DDD / Hexagonal structure

The backend is a Java 17 multi-module Maven reactor under the `cn.lgs.orbisops` package root.

```text
orbisops-types
    shared value types, errors and trace context

orbisops-domain
    aggregates, domain policies, value objects and domain services

orbisops-application
    use cases, ports, commands/queries and process managers

orbisops-infrastructure
    JDBC/MyBatis/Redis persistence and outbound adapter implementations

orbisops-api
    public transport DTOs and response contracts

orbisops-trigger
    HTTP/SSE, scheduling, MCP/runtime adapters and inbound technical orchestration

orbisops-app
    Spring Boot composition root and runtime configuration
```

Architecture tests enforce dependency direction and keep transport/infrastructure concerns out of domain and application policies. Scheduler runtime implementation lives in the trigger adapter layer; the shared `types` module does not depend on Spring Web.

## Requirements

For a full local installation:

- JDK 17
- Maven 3.9+
- Node.js 20+ — used by bundled local MCP server processes
- MySQL 8.x
- PostgreSQL 16 with pgvector
- Redis 7.x
- an OpenAI-compatible chat model endpoint
- an embedding endpoint if RAG ingestion/retrieval is enabled

A dependency-only `docker-compose.yml` is included for MySQL, pgvector, and Redis. It intentionally requires externally supplied credentials.

## Quick start

### 1. Start local dependencies

Provide strong local credentials in your shell and start the dependency stack. Values below are placeholders and must be replaced locally:

```bash
export MYSQL_PASSWORD='[REDACTED_SECRET]'
export MYSQL_ROOT_PASSWORD='[REDACTED_SECRET]'
export PGVECTOR_PASSWORD='[REDACTED_SECRET]'
export REDIS_PASSWORD='[REDACTED_SECRET]'

docker compose up -d
```

The Compose file contains no default password.

### 2. Apply database migrations

The authoritative migration inventory is `db/migrations/manifest.tsv`. Migration `001` is schema-only and creates no default users, model providers, projects, or MCP bindings.

Store MySQL client credentials outside this repository:

```ini
# ~/.config/orbisops/mysql.cnf
[client]
user=orbisops
password=[REDACTED_SECRET]
```

Protect the file and run migrations:

```bash
chmod 600 ~/.config/orbisops/mysql.cnf
MYSQL_DEFAULTS_FILE="$HOME/.config/orbisops/mysql.cnf" ./scripts/db-migrate.sh
```

Every migration is SHA-256 checked before execution and recorded in `orbisops_schema_history`.

### 3. Configure the server

Use `config/orbisops.env.example` as the variable reference. Do not commit real credentials.

Typical production configuration includes:

```bash
export SPRING_PROFILES_ACTIVE=prod
export MYSQL_URL='jdbc:mysql://127.0.0.1:3306/orbisops?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true'
export MYSQL_USERNAME='orbisops'
export MYSQL_PASSWORD='[REDACTED_SECRET]'
export PGVECTOR_URL='jdbc:postgresql://127.0.0.1:5432/orbisops'
export PGVECTOR_USERNAME='orbisops'
export PGVECTOR_PASSWORD='[REDACTED_SECRET]'
export REDIS_PASSWORD='[REDACTED_SECRET]'
export OPENAI_BASE_URL='https://your-openai-compatible-endpoint'
export OPENAI_API_KEY='[REDACTED_SECRET]'
export OPENAI_CHAT_MODEL='your-chat-model'
export ADMIN_JWT_SECRET='[REDACTED_SECRET]'
```

`application-prod.yml` contains the complete set of optional runtime settings.

### 4. Create the first administrator

OrbisOps ships with **no default account and no default password**.

For the first startup only, explicitly enable bootstrap creation:

```bash
export ORBISOPS_BOOTSTRAP_ADMIN_ENABLED=true
export ORBISOPS_BOOTSTRAP_ADMIN_USERNAME='your-admin-name'
export ORBISOPS_BOOTSTRAP_ADMIN_PASSWORD='[REDACTED_SECRET]'
```

The password is validated and encoded by the normal account application service. After the first administrator exists, disable bootstrap:

```bash
export ORBISOPS_BOOTSTRAP_ADMIN_ENABLED=false
```

An existing account is never overwritten by bootstrap.

### 5. Build and run

```bash
mvn clean package
java -jar orbisops-app/target/orbisops-app.jar --spring.profiles.active=prod
```

The default HTTP port is `8099` and can be overridden with `SERVER_PORT`.

## MCP and project onboarding

Generic local MCP server implementations are bundled under `scripts/mcp/`, including readonly adapters for MySQL, PostgreSQL, Redis, RabbitMQ, Prometheus, Elasticsearch, OpenAPI, and Git, plus controlled code/service adapters.

Recommended onboarding flow:

1. Create a project and its resource catalog.
2. Register credentials by reference; do not embed secrets in MCP transport metadata.
3. Generate or register the MCP resource.
4. Discover the real remote tool schema.
5. Review or system-verify the resulting runtime policy.
6. Bind the approved MCP capability to the project default Agent or a specialized Workflow node.
7. Validate and publish the Agent definition before using it from schedules, alerts, channels, or user entry points.

A tool shown in configuration is not considered successful execution evidence unless the authoritative tool result records a real remote call.

## Workflow usage

Use a Workflow when humans already know the macro SOP but some steps still need model autonomy. Typical examples include scheduled health inspection, alert-specific incident diagnosis, user-selected specialized diagnosis, and release/preflight checks.

Use the project default ReAct Agent for open-ended questions where the correct investigation path is not known in advance.

Workflow definitions are versioned and immutable after publication. Runtime execution pins the Agent version and definition hash. Durable execution persists node outputs, loop attempts, routes, tool checkpoints, authority snapshots, and runtime context so recovery can replay completed work safely.

## Change governance

OrbisOps separates preparing a change from applying it:

```text
Diagnosis / Workflow
        ↓
PREPARE_CHANGE
        ↓
isolated code or configuration preparation
        ↓
Test / Build / Evidence
        ↓
ChangePackage
        ↓
Approval
        ↓
independent trusted Landing runtime
        ↓
Verification
   ┌────┴────┐
 success   rollback
```

The original diagnostic run cannot self-promote to `PROD_FULL`.

## Build and verification

Compile the backend reactor:

```bash
mvn -DskipTests compile
```

Run the complete test suite:

```bash
mvn test
```

Run architecture boundaries explicitly:

```bash
mvn -pl orbisops-app -am \
  -Dtest='*ArchitectureTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Run public-release hygiene checks:

```bash
python3 scripts/release-hygiene.py
bash -n scripts/db-migrate.sh
```

The release hygiene check rejects legacy branding, unrelated project fixtures, private local paths, legacy package names, and common secret-like literals in publishable source/configuration.

## Docker image

Build the application first, then build the image:

```bash
mvn -DskipTests package
cd orbisops-app
IMAGE_NAME=orbisops/server IMAGE_TAG=0.1.0 ./build.sh
```

The runtime image is based on Eclipse Temurin 17, includes Node.js for local MCP processes, and runs as the non-root `orbisops` user.

## Security notes

- No administrator, database password, provider key, service token, or JWT secret is embedded in the repository.
- Production database identities should be non-privileged and scoped to required schemas/actions.
- Generated MCP policies remain fail-closed when schema, effect, or review state is unresolved.
- Readonly tools and completed external effects are tracked explicitly for safe durable recovery.
- Runtime code does not infer successful external execution from a successful Java callback alone.
- Keep deployment secrets in a secret manager or platform credential store, not in Git.

## Repository hygiene

Runtime logs, build outputs, local secrets, repair worktrees, process IDs, generated reports, historical evaluation dumps, and unrelated business sample projects are intentionally excluded from the public product tree.

Before publishing a release, run the normal test suite and `scripts/release-hygiene.py`.

## License

Apache License 2.0. See [LICENSE](../LICENSE).
