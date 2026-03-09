# Unified Tool Inventory and Migration Authority

Updated: 2026-08-08
Status: authoritative for phases 639+

## 1. Scope

This inventory classifies Agent-callable capabilities by business ownership and effect. It does not classify a Tool by the transport or storage technology used by its current implementation.

Canonical vocabulary:

- Tool: business capability exposed to Agent, StateGraph or Durable Workflow.
- Provider: source and lifecycle owner of one or more Tools.
- Binding: invocation protocol selected for one Tool.
- Invoker: protocol-specific execution implementation.
- Adapter: infrastructure implementation detail below an Invoker.
- Approved Tool Invocation: an approved `SIDE_EFFECTING` Tool call.
- Compensation: Tool-specific business compensation, not a generic database rollback.

## 2. Inventory decisions

| Current toolset or capability | Current adapter/provider | Business effect | Target binding | Governance | Migration decision |
|---|---|---:|---|---|---|
| `observability.prometheus/*` | `LOCAL_PROMETHEUS` | `READ_ONLY` | MCP for external/customer Prometheus; Internal only for explicitly owned platform telemetry | LOW, no approval, idempotent query | Retain Tool names during compatibility period; disable external Local connection in production profile |
| `observability.logs/elk_*` | `LOCAL_ELASTICSEARCH` | `READ_ONLY` | MCP for external/customer log platforms | LOW, no approval, bounded output | Retain and migrate Provider; do not expose generic HTTP or credentials to Agent |
| `observability.traces/trace_search` | MCP | `READ_ONLY` | MCP | LOW, no approval | Retain; remote declaration is registration input, platform policy remains authoritative |
| `local.logs/*` | `LOCAL_LOG` | `READ_ONLY` | Internal | LOW, no approval, path allowlist | Retain only for Agent Station-owned logs and controlled development/operations profiles |
| `tool_result/*` | `NATIVE` | `READ_ONLY` | Internal | LOW, no approval | Retain as internal Tool Result access API |
| `db.mysql.readonly/*` | `LOCAL_MYSQL` | `READ_ONLY` or validation | MCP for customer/external MySQL; Internal repository calls for Agent Station database | LOW/MEDIUM, no approval, strict SQL guard | Split by resource ownership. Agent Station database never enters Tool Catalog. External Local connection disabled in production |
| `db.mysql.change/mysql_execute_change` | MCP placeholder | `SIDE_EFFECTING` | Concrete business MCP only | HIGH+, operator approval, idempotency and reconciliation required | Generic SQL write Tool forbidden; replace with named business Tools |
| `db.mysql.landing/mysql_config_update` | **RETIRED** | `SIDE_EFFECTING` | None in current runtime | Historical only | Runtime Toolset/handler/writer removed in Phase 652. Keep migration 069 and historical schema/audit data only |
| `cache.redis.readonly/*` | `LOCAL_REDIS` | `READ_ONLY` or validation | Optional MCP for external Redis; prefer business-semantic inspection Tools | LOW/MEDIUM, no approval, namespace and output limits | External Local connection disabled in production; internal Hot Memory Redis is not a Tool |
| `cache.redis.change/redis_mutate` | MCP placeholder | `SIDE_EFFECTING` | Concrete business MCP or Internal Application Service | HIGH+, operator approval, idempotency and reconciliation required | Generic Redis mutation forbidden |
| `cache.redis.landing/*` | **RETIRED** | `SIDE_EFFECTING` | None in current runtime | Historical only | Runtime Toolset/handler/writer removed in Phase 652; use named business MCP Tools for real production mutation |
| `container.docker.readonly/*` | `LOCAL_DOCKER` | mixed read/validation | Sandbox for validation; external operations platform MCP for service state | LOW/MEDIUM | Split validation from production operations; raw Docker access is not a production Tool model |
| `container.docker.landing/*` | **RETIRED** | `SIDE_EFFECTING` | None in current runtime | Historical only | Runtime Toolset/handler/writer removed in Phase 652; deployment/service writes belong to named production MCP Tools |
| `infra.k8s.readonly/k8s_get` | MCP | `READ_ONLY` | MCP | LOW/MEDIUM, no approval, object allowlist | Retain as external Provider Tool |
| `infra.k8s.remediation/k8s_apply` | MCP placeholder | `SIDE_EFFECTING` | Named business operations such as `restart_service`, `scale_service`, `rollback_release` | HIGH/CRITICAL, approval, idempotency, reconciliation | Retire generic apply from production catalog |
| `cicd.jenkins.readonly/*` | MCP | `READ_ONLY` | MCP | LOW, no approval | Retain |
| `cicd.deploy/jenkins_deploy` | MCP placeholder | `SIDE_EFFECTING` | Named deployment Tool | HIGH, approval, receipt and reconciliation | Replace generic deployment entry with typed business schema |
| `config.nacos.readonly/*` | MCP | `READ_ONLY` | MCP | LOW, no approval | Retain |
| `config.nacos.publish/nacos_publish` | MCP placeholder | `SIDE_EFFECTING` | Named configuration business Tool | HIGH, approval, expected state/version, receipt | Retain only after typed contract and server-side idempotency exist |
| `deployment.records/*` | `HTTP_API` | `READ_ONLY` | MCP preferred for external platform; Internal only when Agent Station owns the data | LOW | Migrate protocol without changing Tool business identity |
| `release.platform.execute/*` | MCP placeholder | `SIDE_EFFECTING` | Named release platform MCP | HIGH/CRITICAL | Require Runtime Authority, approved Landing context, provider idempotency and reconciliation |
| `job.platform.execute/*` | MCP placeholder | `SIDE_EFFECTING` | Named job platform MCP | HIGH/CRITICAL | Forbid arbitrary job or shell command surfaces |
| `code.repository/*` | `CODE_REPAIR` | `READ_ONLY` | Internal | LOW, repository allowlist | Retain for registered source repositories |
| `code.repair/*` | `CODE_REPAIR` | repair workspace side effect | Internal plus Sandbox execution where applicable | MEDIUM, isolated workspace, no production target write | Retain; repair workspace mutation is not production Landing |
| `change_package/*` | `CHANGE_PACKAGE` | internal domain command or read | Internal | LOW/MEDIUM, domain authorization | Retain as Agent Station-owned Application Service Tools |
| `inspection.task/*` | `INSPECTION_TASK` | internal domain command or read | Internal | LOW/MEDIUM, project authorization | Retain; do not wrap internal state in MCP |
| `alert.trigger/*` | `ALERT_TRIGGER` | internal domain command or read | Internal | LOW/MEDIUM, project authorization | Retain |
| `channel.notification/*` | `CHANNEL` | read or external notification side effect | Internal coordinator invoking configured Channel adapter | LOW/MEDIUM, destination allowlist; notification compensation usually NONE | Retain as business-semantic Tool, not generic HTTP |
| `skill.catalog/*` | `SKILL` | `READ_ONLY` | Internal | LOW, frozen Work Session scope | Retain |
| Docker validation provider | `SandboxProvider` | validation side effect isolated from production | Sandbox | MEDIUM, pinned image and execution profile | Retain as separate Binding. It is not production Docker Landing |

## 3. Non-Tool infrastructure

The following Agent Station-owned infrastructure must not be published through Tool Catalog or MCP:

- Project, Conversation, Run, Workflow and Checkpoint repositories.
- Audit, Journal, Trusted Proof and Receipt persistence.
- Skill lifecycle persistence.
- Hot Memory, cache and runtime coordination Redis.
- Agent Station schema migrations and internal MySQL tables.
- Internal StateGraph and Durable Workflow state.

They remain behind Repository or Application ports and infrastructure adapters.

## 4. Legacy Landing authority

The following concepts are retired runtime classifications. They may still appear in historical migrations, persisted audit records and implementation logs, but current Tool Catalog/runtime must not publish them:

- MySQL Landing.
- Redis Landing.
- Docker Landing.
- MCP Landing.
- Local Landing.

Their target replacement is:

- `READ_ONLY Tool Invocation`.
- `SIDE_EFFECTING Tool Invocation`.
- `Approved Tool Invocation`.
- `Compensating Tool Invocation`.

Migration 069 and `ai_ops_mysql_landing_receipt` are preserved only as historical schema/data compatibility. The Local MySQL Landing implementation, runtime switch and forward writer are removed; current production target writes use named Tool/MCP providers through ToolExecution.

## 5. First business write candidate

Phase 645 must choose exactly one low-risk, typed operation, preferably `UpdateAlertThreshold` or `ChangeRateLimit`. It must not reuse generic SQL, Redis command, shell, HTTP or Docker surfaces.

Required contract:

- typed input and output schemas;
- project and actor authorization;
- parameter range validation;
- expected state and expected version;
- execution key and server-side idempotency;
- stable receipt and result hash;
- query-by-execution-key reconciliation;
- deadline;
- stable reason codes;
- Runtime Authority and environment-scoped Tool exposure; approval is package-level, not a second per-Tool rollout ACL.

## 6. Production target state

Production may contain only these Binding categories:

- Internal: Agent Station-owned domain capabilities.
- MCP: external/customer systems.
- Sandbox: isolated validation and code execution.

`LOCAL` remains a compatibility projection during migration and must not authorize external production resource access by itself.
