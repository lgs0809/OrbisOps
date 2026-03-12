# ADR: Unified Tool Definition, Binding and Invocation

- Status: Accepted
- Date: 2026-08-03
- Phases: 639–648
- Scope: Agent-callable Tool catalog, invocation and approved side effects

## Context

Agent Station historically accumulated two parallel execution vocabularies:

```text
Tool execution
├── Local dispatch
└── MCP dispatch

Approved landing
├── Local landing
└── MCP landing
```

The implementation is operational, but the distinction leaked infrastructure choices into business governance. MySQL, Redis, Docker, Local and MCP began to look like different Tool categories even though authorization, approval, risk, idempotency and reconciliation depend on business effect rather than transport.

The existing `ToolExecutionApplicationService` already provides the authoritative common execution spine:

- Tool catalog resolution;
- policy decision;
- persistent idempotency reservation;
- dispatch;
- Tool Result and Evidence recording;
- checkpoint and audit projection;
- authoritative completion and reconciliation.

Creating a second execution framework would duplicate this authority and risk divergent policy, receipt and completion semantics.

## Decision

### 1. Tool is the only upper-layer business capability

Agent, StateGraph, Durable Workflow and approved change execution address a Tool by its business identity. They do not select MySQL, Redis, Docker, Local or MCP as a business operation type.

### 2. Definition, governance and binding are separate facts

A Tool is represented by:

```text
Tool Definition
├── identity, description and schema
├── Tool Governance
│   ├── effect
│   ├── risk
│   ├── approval requirement
│   ├── idempotency capability
│   ├── reconciliation capability
│   ├── compensation capability
│   └── trust level
└── Tool Binding
    ├── Internal
    ├── MCP
    └── Sandbox
```

Provider and Binding never determine approval by themselves. An MCP Tool may be read-only, and an Internal Tool may be high risk.

### 3. Bindings are invocation protocols

Canonical bindings are:

- `InternalToolBinding`: Agent Station-owned Application Service or internal read model.
- `McpToolBinding`: external/customer Tool exposed by an MCP Provider.
- `SandboxToolBinding`: isolated validation or code execution environment.

`LOCAL` remains only a compatibility provider projection while existing handlers migrate. It maps to Internal Binding during the compatibility phase and must not imply production safety.

### 4. Invokers implement bindings

The protocol SPI is `ToolInvoker<B extends ToolBinding>`.

Initial implementations:

- `OpsInternalToolInvoker` delegates existing non-MCP handlers.
- `OpsMcpToolInvoker` delegates the existing MCP execution handler.
- `OpsSandboxToolInvoker` is fail-closed until an explicit Tool-to-Sandbox contract is bound.

The composite dispatch path resolves a Binding before selecting an Invoker. It no longer chooses an upper-layer branch directly from `adapterType`.

### 5. Existing authoritative execution remains single-sourced

`ToolInvocationCoordinator` is a provider-neutral facade over `ToolExecutionApplicationService`. It does not duplicate policy, idempotency, audit, receipt or completion reconciliation.

The stable caller result is:

```text
ToolInvocationResult
├── SUCCEEDED | BLOCKED | FAILED | UNKNOWN
├── reasonCode
├── executedProductionAction
├── receiptId
├── resultHash
└── output
```

Only a successful approved target-resource write may claim `executedProductionAction=true`.

### 6. Landing is a governed side-effecting invocation

Landing is redefined as an approved `SIDE_EFFECTING` Tool Invocation. Provider type is not part of its business identity.

Compatibility class names such as Local Landing and MCP Landing may remain temporarily, but future upper layers must converge on an approved Tool invocation coordinator.

### 7. Resource ownership determines Internal versus MCP

- Agent Station-owned domain state and infrastructure remain Internal Repository/Application calls.
- External systems and customer resources use MCP in production.
- Sandbox remains a separate provider for isolated validation.

Agent Station internal MySQL, Redis, Journal, Audit, Checkpoint and Workflow state are not Tools and are not exposed through MCP.

### 8. Production writes are named business Tools

Production must not expose:

- arbitrary SQL;
- arbitrary Redis commands;
- arbitrary shell;
- arbitrary HTTP;
- arbitrary Docker or Kubernetes commands.

Production side effects are represented by small typed Tools such as `update_alert_threshold`, `change_rate_limit`, `restart_service` or `trigger_deployment`, with explicit authorization, approval, expected state, idempotency, receipt and reconciliation.

## Consequences

### Positive

- One catalog and one governance pipeline for Internal, MCP and Sandbox Tools.
- Provider migration does not change Tool business identity.
- Approval and risk are testable independently of transport.
- Existing phase 629–638 authority remains intact.
- Local handlers can migrate incrementally without a big-bang rewrite.

### Costs

- Legacy adapter/provider fields remain during migration.
- Existing Local and MCP handlers require compatibility Invokers.
- Landing classes cannot be deleted until approved change execution is moved to the unified coordinator.
- Provider readiness and production profile checks require a later explicit phase.

## Rejected alternatives

### Make every capability an MCP Tool

Rejected because Agent Station-owned repositories, Workflow state, Checkpoint, Journal, Audit and Hot Memory are internal domain/infrastructure concerns. Wrapping them in MCP adds a remote boundary without ownership benefit.

### Keep one framework per infrastructure technology

Rejected because MySQL, Redis, Docker and MCP are not business effects. This produces duplicated authorization, approval, audit and error mapping.

### Replace the existing Tool execution service immediately

Rejected because it already contains the authoritative idempotency and completion protocol. Replacing it would create unnecessary migration risk.

### Treat compensation as database rollback

Rejected because many side effects are irreversible or require a different business operation. Compensation is Tool-specific and may be NONE, MANUAL, COMPENSATING_TOOL or STATE_RESTORE.

## Migration sequence

1. Inventory and ADR.
2. Add governance and Binding projections while retaining legacy fields.
3. Resolve Binding and invoke through typed Invokers.
4. Expose the provider-neutral invocation coordinator.
5. Converge approved Landing on side-effecting Tool invocation.
6. Disable generic Local Landing by default.
7. Move external read-only resources to MCP.
8. Implement one typed business write MCP and its specific compensation policy.
9. Enforce production profile/readiness.
10. Complete staging fault-injection acceptance.

## Verification rules

Architecture tests must prevent:

- approval decisions based solely on Provider type;
- new upper-layer `if LOCAL` / `if MCP` branches;
- direct Agent access to internal database or Redis infrastructure;
- generic SQL, Redis, shell, HTTP or Docker production write Tools;
- a `BLOCKED` result generating passed proof or production-action claims.

The full staging acceptance in phase 648 remains an explicit non-claim until performed in a real staging environment.
