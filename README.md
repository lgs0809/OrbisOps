<div align="center">

# OrbisOps

**Governed Agentic Operations Platform**

Investigate → Automate → Approve → Land → Verify

[![GitHub Actions](https://img.shields.io/badge/-E9F3FF?style=flat-square&logo=githubactions&logoColor=2088FF)](https://github.com/lgs0809/OrbisOps/actions/workflows/server-ci.yml)[![Server CI](https://img.shields.io/github/actions/workflow/status/lgs0809/OrbisOps/server-ci.yml?branch=main&style=flat-square&label=Server%20CI&labelColor=2D333B)](https://github.com/lgs0809/OrbisOps/actions/workflows/server-ci.yml) [![GitHub Actions](https://img.shields.io/badge/-E9F3FF?style=flat-square&logo=githubactions&logoColor=2088FF)](https://github.com/lgs0809/OrbisOps/actions/workflows/web-ci.yml)[![Web CI](https://img.shields.io/github/actions/workflow/status/lgs0809/OrbisOps/web-ci.yml?branch=main&style=flat-square&label=Web%20CI&labelColor=2D333B)](https://github.com/lgs0809/OrbisOps/actions/workflows/web-ci.yml)
[![GitHub](https://img.shields.io/badge/-E8E8E8?style=flat-square&logo=github&logoColor=181717)](https://github.com/lgs0809/OrbisOps/tags)[![Release](https://img.shields.io/github/v/tag/lgs0809/OrbisOps?sort=semver&style=flat-square&label=release&color=5F70E1&labelColor=2D333B)](https://github.com/lgs0809/OrbisOps/tags)
![OpenJDK](https://img.shields.io/badge/-FDF3E6?style=flat-square&logo=openjdk&logoColor=ED8B00)![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&labelColor=2D333B)
![Spring Boot](https://img.shields.io/badge/-F0F7EC?style=flat-square&logo=springboot&logoColor=6DB33F)![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=flat-square&labelColor=2D333B)
![React](https://img.shields.io/badge/-EAFBFF?style=flat-square&logo=react&logoColor=61DAFB)![React 18](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&labelColor=2D333B)
![PostgreSQL](https://img.shields.io/badge/-ECF0FC?style=flat-square&logo=postgresql&logoColor=4169E1)![PostgreSQL + pgvector](https://img.shields.io/badge/PostgreSQL-pgvector-4169E1?style=flat-square&labelColor=2D333B)
[![Apache](https://img.shields.io/badge/-FBE9EA?style=flat-square&logo=apache&logoColor=D22128)![License](https://img.shields.io/badge/License-Apache--2.0-D22128?style=flat-square&labelColor=2D333B)](LICENSE)

</div>

OrbisOps brings investigation, automation, workflows, MCP tools, knowledge retrieval, and controlled production changes into one operations workspace.

It uses a ReAct Agent for open-ended diagnosis and versioned Workflows for repeatable procedures, while keeping production authority outside model reasoning.

## Quick Start

Requirements: **Docker + Docker Compose v2**.

```bash
git clone <your-orbisops-repository-url>
cd orbisops
cp deploy/orbisops.env.example deploy/orbisops.env
```

Edit `deploy/orbisops.env` and replace the infrastructure and authentication placeholders required by the deployment preflight, especially the MySQL, PostgreSQL, Redis, and administrator JWT secrets. Model-provider credentials can be configured later.

Start OrbisOps:

```bash
make up
```

Open the web console at `http://localhost:3002`. On a fresh installation, OrbisOps shows the first-time setup flow in the browser; create the first administrator there and you will be signed in automatically.

The backend API is available at `http://localhost:8099` by default. Both ports can be changed in `deploy/orbisops.env`.

For unattended or automated deployments, OrbisOps also supports optional headless administrator bootstrap through the `ORBISOPS_BOOTSTRAP_ADMIN_*` variables. It is disabled by default and is not required for normal installation.

Stop the stack with:

```bash
make down
```

## What You Get

- **ReAct Agent** — open-ended investigation, diagnosis, tool use, and evidence collection.
- **Visual Workflows** — versioned, repeatable procedures with a stable macro flow and optional Agent-powered steps.
- **Tools & MCP** — project-scoped tools, MCP resources, capability boundaries, and progressive disclosure.
- **Knowledge & Memory** — RAG, optional reranking and multimodal retrieval, conversation context, and semantic memory.
- **Automation & Channels** — scheduled inspections, alert-driven execution, and messaging integrations.
- **Governed Changes** — `ChangePackage → Approval → Landing → Verification` instead of unrestricted production mutation.

## Model Providers

OrbisOps does not bundle or start an LLM, embedding model, or reranker. Connect the services you operate or obtain from a provider after the control plane is running.

Provider endpoints and model selections are managed in OrbisOps. Credentials are referenced from deployment environment variables or your secret-management system rather than stored as raw browser-entered API keys.

## Architecture

```text
Chat / Alert / Schedule / Channel
              │
              ▼
       ReAct Agent / Workflow
              │
              ▼
      Tools · MCP · RAG · Memory
              │
       diagnose / propose change
              │
              ▼
ChangePackage → Approval → Landing → Verification
```

ReAct handles tasks where the next step depends on what the Agent discovers. Workflows handle procedures whose macro structure should remain stable and reproducible. Production changes use the governed change path rather than giving the model unrestricted mutation authority.

## Development

Run the repository checks with `make verify`. Backend and frontend development notes are in [`server/README.md`](server/README.md) and [`web/README.md`](web/README.md).

## License

Apache License 2.0. See [`LICENSE`](LICENSE).
