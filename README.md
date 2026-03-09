# OrbisOps

**Governed Agentic Operations Platform**

OrbisOps is an agentic operations platform for investigation, repeatable operational workflows, and governed production changes. It combines autonomous ReAct execution with explicit workflow orchestration, evidence, approval, Landing, verification, rollback, and durable audit boundaries.

## Why OrbisOps

Operations work has two very different shapes:

- **Open-ended investigation** where the next action depends on what the Agent discovers.
- **Repeatable SOPs** where humans already know the macro process but individual steps may still require model reasoning or tool use.

OrbisOps supports both in one governed runtime:

- project default **ReAct Agents** for open-ended work;
- published **Workflows** for repeatable SOPs;
- `DIRECT`, `LLM`, and `REACT` execution modes on Agent nodes;
- MCP, Skills, RAG, code workspaces, memory, and tools as Agent capabilities;
- `ChangePackage → Approval → Landing → Verification / Rollback` for production mutation;
- durable execution and evidence-aware audit records.

## Repository structure

```text
orbisops/
├── server/                  # Java 17 / Spring Boot backend
├── web/                     # React / TypeScript web console
├── .github/workflows/       # Monorepo CI
├── Makefile                 # Unified developer entry points
├── LICENSE                  # Apache-2.0
└── README.md
```

This is a **single Git repository and a single product**. The frontend and backend remain separate technical modules so they can be developed and deployed independently when useful.

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

A normal run can investigate systems and, when explicitly authorized, prepare a change. It cannot self-escalate into a production-write runtime. Production mutation is executed by a separate trusted Landing runtime after approval.

## Requirements

### Backend

- JDK 17
- Maven 3.9+
- MySQL 8.x
- PostgreSQL 16 + pgvector
- Redis 7.x
- Node.js 20+ for bundled local MCP processes
- an OpenAI-compatible model endpoint

### Web console

- Node.js 20+
- npm 10+

## Quick verification

Run the complete repository verification gate:

```bash
make verify
```

Or run each side independently:

```bash
make verify-server
make verify-web
```

## Backend

The backend uses a DDD / Hexagonal multi-module Maven structure under the `cn.lgs.orbisops` package root.

```text
server/
├── orbisops-types/
├── orbisops-domain/
├── orbisops-application/
├── orbisops-infrastructure/
├── orbisops-api/
├── orbisops-trigger/
└── orbisops-app/
```

See [server/README.md](server/README.md) for architecture, local dependencies, migrations, configuration, MCP onboarding, workflows, change governance, and runtime details.

## Web console

The React/TypeScript console contains Agent chat, execution history, Workflow design and publication, MCP/model/Skill/RAG configuration, alerts/incidents, ChangePackage approval, Landing status, and audit views.

See [web/README.md](web/README.md) for frontend development and deployment.

## Local development

Start backend dependencies from the server directory after supplying local credentials:

```bash
cd server
docker compose up -d
```

Run the backend:

```bash
cd server
mvn clean package
java -jar orbisops-app/target/orbisops-app.jar
```

Run the web console in another terminal:

```bash
cd web
npm ci
PUBLIC_API_BASE_DOMAIN=http://127.0.0.1:8099 npm run dev
```

The backend defaults to port `8099` unless overridden.

## Security defaults

OrbisOps intentionally ships with:

- no default administrator account;
- no default password;
- no committed database credentials;
- no committed model API keys;
- no private deployment endpoint;
- fail-closed production mutation boundaries.

Use the example configuration files under `server/config/` and `web/config/` as references. Never commit real secrets.

## CI

GitHub Actions verifies backend and frontend changes independently while keeping them in one repository:

- backend release-hygiene checks, migration/MCP syntax checks, and Maven tests;
- frontend release-hygiene checks, type checking, Vitest tests, and production build.

## License

Apache License 2.0. See [LICENSE](LICENSE).
