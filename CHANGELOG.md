# Changelog

OrbisOps follows Semantic Versioning for public releases. The milestones below map the repository's development history into product versions; compatibility-sensitive database fields, event codes and persisted identifiers are not renamed solely to match a release number.

## 2.0.0 — 2026-08-17

- Unified OrbisOps-owned Spring configuration under `orbisops.*` and public environment variables under `ORBISOPS_*`.
- Removed bundled fixed-model deployment tooling and made LLM, embedding, rerank and multimodal capabilities provider-neutral and opt-in.
- Added a portable source-build Docker Compose deployment with MySQL, PgVector, Redis, migration, backend and same-origin web proxying.
- Added repository-wide release hygiene, Checkstyle, architecture gates, frontend verification and browser acceptance gates.
- Reworked public documentation and deployment instructions for a standalone clone.

## 1.8.0 — 2026-07-20

- Closed release-candidate cold-start, bundled MCP runtime and project-context issues.
- Added CI release gates and end-to-end release acceptance coverage.

## 1.5.0 — 2026-06-18

- Completed the Phase 4 product surface: governed audit controls, accessibility/responsive behavior and project activation telemetry.
- Added reproducible product demo assets.

## 1.2.0 — 2026-06-01

- Added secure WeChat Official Account support, the channel compatibility matrix and the global command palette.

## 1.0.0 — 2026-05-23

- Completed the Phase 3 frontend/server-state convergence and reactor verification.
- Established project-scoped management surfaces for changes, incidents, agents, skills, MCP, memory, governance and operational configuration.

## 0.5.0 — 2026-03-19

- Completed the initial product/workspace refactor around ReAct, Workflow and governed change execution.
- Established the OrbisOps product identity and core project/channel UX.

## 0.1.0 — 2026-03-09

- Initial OrbisOps repository baseline.
