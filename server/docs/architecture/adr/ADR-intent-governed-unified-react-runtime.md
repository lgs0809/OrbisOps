# ADR: Intent-Governed Unified ReAct Runtime

- Status: **SUPERSEDED**
- Date: 2026-08-06
- Scope: `orbisops`

> Superseded on 2026-08-08 by the runtime model implemented in Phase 652 (`652-react-workflow-runtime-environment-and-landing-simplification.md`). Business Intent classification is no longer the execution gateway; ReAct/Workflow execution style is orthogonal to Runtime Authority, and Landing uses the platform-fixed ReAct runtime with environment-scoped `PROD_FULL` resources.

## Context

The project previously exposed several product-level execution concepts at the same boundary: Chat, Graph, Hybrid, AgentScope/ReAct, specialized workflow auto-selection, a separate preparation tool chain, and a fixed landing runtime. This made intent routing, authorization and recovery semantics depend on request metadata or Agent Definition fields. In particular, a natural-language request could implicitly choose a specialized workflow, and `phase` or `capabilities` in a definition could influence tool-call stage.

The target architecture requires one recoverable Agent microkernel while preserving existing StateGraph, ReAct node, WorkSession, checkpoint, cancellation, idempotency and landing reliability capabilities.

## Decision

### 1. Intent governance is the only execution gateway

Every user or system request is normalized into an `OpsIntentDecision`. The decision exposes a typed `ExecutionDisposition`:

- `DIRECT_RESPONSE`
- `FIXED_CONTROL_ACTION`
- `DEFAULT_REACT`
- `EXPLICIT_AGENT`
- `MULTI_INTENT_SEQUENCE`
- `CLARIFICATION`
- `REJECTED`

Blocking and clarification take precedence, followed by multi-intent ordering, fixed control actions, explicit Agent binding, default ReAct execution and direct response.

Natural-language text must not auto-select a specialized workflow. A specialized Agent can execute only through an explicit Agent identifier or a fixed server-side action. UI recommendation and catalog search may still exist, but they are not runtime authority.

### 2. One product-level Agent runtime

`UnifiedAgentRuntime` is the public microkernel facade. All Agent execution plans select `OpsUnifiedAgentEngineAdapter` with key `UNIFIED_STATE_GRAPH`.

`SIMPLE`, `MULTI_TURN` and `AGENT` are execution policies, not separate engines. AgentScope/ReAct, visual workflow and hybrid definitions are compiled as StateGraph node shapes. The legacy Chat, Graph, Hybrid and AgentScope adapters remain temporarily registered only for historical compatibility and are marked deprecated.

The unified runtime continues to use the existing WorkSession lifecycle, durable checkpoints, cancellation, event journal, memory policy and analysis subgraphs.

### 3. Server-created execution authority

Each run receives an immutable `AgentRunExecutionContext` containing:

- run, WorkSession and project identity;
- trusted trigger source and `ExecutionDisposition`;
- `AgentExecutionStage`;
- frozen Agent version, definition hash, prompt hash, model profile and capability bindings;
- optional approval-frozen package snapshot;
- `CapabilityProfile`;
- allowed resources, allowed tools and deadline.

The context is created by server-side use cases. Client metadata, prompt text and Agent Definition fields are not accepted as stage or permission authority.

### 4. Stage and capability model

Trusted stages are:

| Stage | Purpose | Maximum profile |
|---|---|---|
| `INVESTIGATE` | diagnosis, observation and analysis | `DATA_READONLY` or `PROD_DIAGNOSTIC` |
| `PREPARE` | controlled code/build verification and ChangePackage compilation | `TEST_FULL` |
| `LANDING` | approval-bound production execution | `PROD_LANDING` |

The runtime enforces the following boundaries:

- `DATA_READONLY` and `PROD_DIAGNOSTIC` can use only read-only resources.
- `TEST_FULL` may write only to non-production resources; production resources remain read-only.
- `PROD_LANDING` requires a new run and a typed `ApprovedPackageSnapshot`.
- A definition's `phase`, `lifecycle` or `capabilities` cannot elevate a run.
- Missing trusted context fails closed to `INVESTIGATE` and read-only behavior.
- Direct transition to `LANDING` is forbidden; approval creates a new run.

Project resource descriptors project environment, permission profile, allowed stages, read-only flag, risk level, effect ceiling, terminal policy and allowed roots into MCP runtime configuration. MCP resolution applies the profile before exposing callbacks.

### 5. Prepare uses the owning Agent run

The Agent that owns the current WorkSession also owns PREPARE. Requests cannot switch to a second preparation graph or a built-in default preparation Agent.

`OpsChangePackagePreparationService` is a deterministic package compiler and validator. It consumes evidence and verified tool results already produced by the owning PREPARE run. It does not start another MCP/tool execution chain.

The historical `default-preparation-agent` runtime resource and admin discovery endpoint are physically retired. Persisted historical snapshots may still contain the identifier, but current preparation resolves only the owning WorkSession Agent snapshot/project default and never falls back to a second preparation Agent.

### 6. Landing is a platform-owned ReAct run bound to an approved package

A fixed landing action validates the approved package and produces an `ApprovedLandingAgentCommand`. `OpsLandingAgentRunAuthorizationService` freezes the exact approved package version/hash and the trusted runtime context required to start a new LANDING run.

`OpsApprovedLandingAgentRunCoordinator` then:

1. instantiates the platform-owned `platform-landing-react` definition rather than resolving a user-selected Agent or Workflow;
2. creates a new trusted `LANDING` run with `PROD_FULL` runtime authority;
3. executes it through the same `UnifiedAgentRuntime` / StateGraph infrastructure used by ordinary ReAct runs;
4. exposes production tools only through the unified Runtime Authority + ToolExecution boundary, including production MCP and Progressive MCP when the project runtime profile permits them.

The ChangePackage is the approved task book and audit snapshot, not a second per-tool RBAC language. Landing may autonomously choose tool order, diagnostics and retries while the approved plan remains valid. If execution fails but the plan is still valid, the result is `LANDING_FAILED`; if continuing would materially change the approved plan, the result is `NEEDS_REPLAN`, which returns the package to revision and requires a new approval.

The former deterministic `OpsChangePackageLandingRuntime`, `ApprovedLandingControl`, Local/MCP forward executors and per-operation Landing ACL path have been removed from production code. The historical Landing operation journal remains only as a recovery/reconciliation store for old UNKNOWN facts; current production side effects are governed and deduplicated through ToolExecution. Any unresolved ToolExecution side effect blocks later Landing tool execution and prevents a false `LANDED` result until reconciliation is completed.

## Consequences

### Positive

- Default Chat, explicit Workflow and Landing have distinct execution-style selection rules while sharing one runtime substrate.
- All Agent-capable entry points converge on one Runtime Authority and ToolExecution security boundary.
- Agent YAML, request metadata and ChangePackage operation details cannot self-elevate production permissions.
- PREPARE uses the owning run's verified results instead of starting a hidden second tool/MCP chain.
- LANDING is bound to an approved package version/hash while retaining autonomous ReAct execution within the approved plan.

### Trade-offs

- Historical recovery tables and adapters remain for reconciliation of old UNKNOWN Landing facts, but they are not forward production writers.
- Resource descriptors without governance metadata are treated conservatively as read-only compatibility resources.
- External staging acceptance still requires configured MCP services, credentials and a production-like environment.

## Migration and removal criteria

Legacy engine adapters may be removed only after:

1. no persisted Agent definition or WorkSession references their keys;
2. all API clients use the unified runtime capability response;
3. recovery tests prove old checkpoints can be migrated or retired;
4. one release has emitted no compatibility-adapter usage telemetry.

The historical default preparation Agent resource and admin discovery endpoint may be removed after all stored packages carry an explicit preparation Agent snapshot and no client requests the compatibility endpoint.

## Verification

The architecture is protected by unit and source-boundary tests covering:

- disposition precedence and multi-intent ordering;
- disabled natural-language specialized workflow selection;
- unified runtime routing for all modes and definition shapes;
- server-derived Stage/Profile and client-forgery rejection;
- diagnostic/test/production MCP capability boundaries;
- absence of a second PREPARE tool chain;
- approval snapshot action-space freezing;
- LANDING creation through `UnifiedAgentRuntime` before deterministic control execution.
