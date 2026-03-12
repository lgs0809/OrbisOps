# orbisops 战略 DDD Context Map

更新时间：2026-08-14

## 1. 文档地位

本文档定义 `orbisops` 的战略 DDD 边界，是新增模块、跨模块依赖、聚合所有权和 Application Port 设计的权威约束。

需要特别区分：

- Java 顶层包不必然等于一个限界上下文；
- 一个限界上下文可以由多个紧密协作的 Domain 包组成；
- Trigger、Infrastructure、API 是技术层，不是业务限界上下文；
- `domain.shared` 只允许承载真正稳定、无业务所有权争议的 Published Language；
- 动态 JSON、MCP Schema、LLM metadata 可以存在，但必须被具名边界对象包裹，不能成为跨上下文默认语言。

## 2. 核心域、支撑域与通用域

### 2.1 核心域

平台的核心竞争力是“受控运维 Agent 从调查到变更落地的可信闭环”，由以下上下文共同形成：

1. **Work Session & Investigation**：任务运行、意图、调查、分析编排和运行态上下文。
2. **Controlled Change**：ChangePackage 创建、校验、审批、版本、Landing 计划和状态机。
3. **Execution Governance**：工具/MCP 执行、目标路由、策略门禁、执行记录和资源治理。
4. **Evidence & Trust**：证据、Tool Result、Trusted Proof、审计事实和可信引用。

### 2.2 支撑域

- Project Workspace
- Agent Definition & Capability
- Knowledge & RAG
- Skill Lifecycle
- Memory
- Channel
- Alert & Incident
- Code Repair

### 2.3 通用域

- Security Identity
- Canonical JSON / hashing 等无业务所有权的稳定基础能力
- Statistics / read model
- Model policy

## 3. 限界上下文目录

| 限界上下文 | 主要 Domain 包 | 业务所有权 | 典型聚合/一致性边界 |
|---|---|---|---|
| Project Workspace | `domain.project`、`domain.source` | Project 定义、成员、资源、项目 MCP/能力授权与源码目录 | ProjectDefinition、ProjectResource、ProjectMcp、ProjectMember、SourceRepository |
| Agent Definition & Capability | `domain.agentdefinition`、`domain.agent` | Agent 定义、版本、Graph、能力绑定、兼容配置和任务计划 | AgentDefinition Current/Version、DefinitionGraph、CapabilityBinding、TaskSchedule |
| Work Session & Investigation | `domain.worksession`、`domain.runtime`、`domain.analysis`、`domain.investigation`、`domain.chatsession` | Work Session、Run、Graph、调查计划、分析任务、会话参与者和运行态快照 | WorkSessionRun、ChatSession、AnalysisRun、TaskContext、RuntimeContextBundle |
| Controlled Change | `domain.changepackage` | 变更包版本、审批、校验、Landing 计划、操作日志和状态跃迁 | ChangePackage、ChangePackageVersion、Approval Ledger、Landing Operation |
| Execution Governance | `domain.execution`、`domain.toolexecution`、`domain.mcpexecution`、`domain.mcp`、`domain.toolset` | 解析共享执行选择、执行资源、工具集、MCP 策略/快照/激活、远端调用和执行记录 | ExecutionResource、ExecutionAdapterTemplate、McpPolicy、McpSnapshot、ToolExecution |
| Evidence & Trust | `domain.evidence`、`domain.audit` | 证据、工具结果、可信 Proof、配置/分析审计事实 | ToolResult、Evidence、TrustedProof、ConfigAudit、AnalysisAudit |
| Knowledge & RAG | `domain.knowledge`、`domain.rageval` | 知识库、文档、检索策略、摄取任务、反馈和质量评测 | KnowledgeBase、KnowledgeDocument、IngestionJob、RetrievalPolicy、RagEvalRun |
| Skill Lifecycle | `domain.skill` | Skill Catalog、不可变 Package、版本指针、运行选择、评测和进化 | SkillCatalog Pointer、SkillPackage、EvolutionJob、Eval Suite |
| Memory | `domain.memory` | Hot/Cold/Semantic/Governed/Context Memory 的生命周期和检索规则 | MemoryItem、GovernedMemory、ContextMemory、SemanticMemory |
| Channel | `domain.channel` | 消息 Transport 配置、身份映射、入站/出站、Outbox、租约和投递终态；入站执行只引用共享 ExecutionBinding | Channel、ChannelIdentity、InboundMessage、OutboundMessage、ChannelOutbox |
| Alert & Incident | `domain.alert`、`domain.incident`、`domain.statistics`、`domain.agenteval`、`domain.modelpolicy` | 告警规则/聚合/Outbox、Incident、评测门禁和运营读模型 | AlertRule、AlertAggregation、AlertOutbox、Incident、AgentEvalRun |
| Code Repair | `domain.repair` | 基于精确源码 revision 的修复工作区、受控代码编辑、构建验证、修复提交与制品身份 | RepairWorkspace、CodeDelivery、Verified Repair Artifact |
| Security Identity | `domain.security` | 管理员账号状态、角色和凭据不变量 | AdminUserAccount |

> `domain.sandbox` 及其 Application/Trigger/JDBC 子系统已退出当前产品写路径。历史数据库列、Trusted Proof 来源或旧配置名可以继续作为只读/迁移兼容事实存在，但不得重新成为新的聚合、Tool Provider 或 ChangePackage 前置门禁。

## 4. Context Map

### 4.1 上游/下游关系

```text
Project Workspace
    ├──> Agent Definition & Capability
    ├──> Knowledge & RAG
    ├──> Skill Lifecycle
    ├──> Execution Governance
    └──> Work Session & Investigation

Agent Definition & Capability ──> Work Session & Investigation
Knowledge & RAG              ──> Work Session & Investigation
Memory                       ──> Work Session & Investigation
Skill Lifecycle              ──> Work Session & Investigation
Channel                      ──> Work Session & Investigation

Work Session & Investigation ──prepare──> Controlled Change
Controlled Change            ──approved plan──> Execution Governance
Execution Governance         ──result/proof──> Evidence & Trust
Evidence & Trust             ──trusted facts──> Work Session / Controlled Change

Code Repair                  ──verified commit/diff/test/artifact proof──> Controlled Change
Alert & Incident             <──events/outcomes── Work Session / Execution / Channel
```

### 4.2 关系类型

- **Project Workspace → 其他上下文**：Customer/Supplier。Project 发布项目身份、成员和资源目录，下游不得反向修改 Project 聚合。
- **Agent Definition → Work Session**：Published Language。运行时只消费已发布 Definition Snapshot，不直接维护 Definition 当前指针。
- **Knowledge/Memory/Skill → Work Session**：Open Host Service + Published Language。通过 Application Port 提供检索或冻结快照。
- **Work Session → Controlled Change**：Customer/Supplier。Work Session 只能提交 Prepare/Review 命令，不得直接推进 ChangePackage 状态。
- **Controlled Change → Execution Governance**：Conformist to approved snapshot。执行侧必须服从已批准快照、proof、fencing 和幂等约束。
- **Code Repair → Controlled Change**：Published Language。只发布 VERIFIED Repair Workspace 的 commit/diff/test/artifact 身份；ChangePackage 不接收第二套 Sandbox 会话或自报告验证状态。
- **外部 MCP/LLM/IM/Git/数据库 → 内部上下文**：Anti-Corruption Layer。外部协议、SDK 对象、JSON key 和框架类型必须在 Adapter 中转换。
- **Evidence & Trust → 多上下文**：Published Language。只发布不可变证据引用和可信状态，不发布 Repository 实体。
- **Security Identity → Trigger/API**：ACL。认证技术细节属于外层；Domain 只拥有账号和凭据不变量。

### 4.3 ExecutionBinding：入口与执行方式的共享 Published Language

Chat、Channel、Schedule、Alert、Inspection 是不同入口，不拥有第二套 Agent 路由语言。它们统一发布/消费 Shared Kernel `types.execution.ExecutionBinding`；任何业务 bounded context 都不得把它重新定义成自己的 Agent 路由 DTO：

```text
REACT
WORKFLOW(workflowId, LATEST_PUBLISHED | PINNED_VERSION, version?)
NONE  # 仅允许在纯 Transport/输出型入口，例如仅通知 Channel
```

- `REACT` 是产品级默认智能执行方式；内部 `defaultAgentId/version/hash` 只在 Application/Runtime 边界解析并冻结，不暴露给普通用户。
- `WORKFLOW` 指向用户命名、已发布的固定 Graph；一次 Run 启动后必须冻结 exact Definition version/hash。
- Channel 是 Transport。`NONE` 允许纯通知/主动发送 Channel；`REACT/WORKFLOW` 只定义入站消息交给谁处理，不改变 Channel 的 HMAC、身份、租约、去重或 Outbox 语义。
- Schedule/Alert 可以继续使用既有 exact Agent snapshot 做 durable replay，但产品层必须投影成 `ReAct + 已发布 Workflow`，不得要求用户选择内部 MAIN_ASSISTANT Definition。
- Landing 不消费用户 ExecutionBinding；生产执行继续固定使用平台 `platform-landing-react` 与批准后的 PROD_FULL Runtime Authority。

## 5. 聚合与一致性边界

1. 不以 Controller 请求、数据库表或页面为聚合边界。
2. 一个 Application Use Case 可以协调多个聚合，但不得伪造跨聚合强事务；需要原子性时使用明确 Unit of Work。
3. ChangePackage Current、Version、Approval、Event 和 Landing Operation 各自有独立持久化事实，状态跃迁必须由 Domain Policy/Aggregate 与 CAS 双重约束。
4. WorkSessionRun、ChatSession、TaskContext 和 RuntimeContextBundle 是不同一致性边界，通过标识和版本关联，不合并为一个巨型聚合。
5. Skill Package 和 Definition Version 是不可变版本；Current Pointer 使用 CAS，不允许覆盖式更新历史版本。
6. Channel Outbox、Alert Outbox 和执行记录采用耐久消息/租约语义，不与远端副作用假设为同一事务。
7. Evidence、ToolResult、TrustedProof 是 append/immutable 事实；业务上下文保存引用，不复制并修改可信事实。
8. Project 是授权和资源归属的权威来源，但不拥有 Agent、Knowledge、Skill、MCP 的内部生命周期。
9. Project MCP 的项目侧风险语言由 `ProjectMcpRiskLevel` 拥有；MCP Governance 的风险语言属于 Execution Governance。二者只允许在 Trigger ACL/专用 Adapter 中显式翻译，Project Domain 不得复用 MCP Governance 枚举。

## 6. 跨上下文集成规则

### 6.1 允许

- Domain 使用同一上下文内的 model/service/repository port。
- Domain 使用 `domain.shared` 的稳定 Published Language。
- 经明确评审后，一个上下文可以只读引用另一个上下文的不可变 Published Language，例如 Analysis 展示策略读取 `GraphEvent`。
- Application 通过窄 Port、Command、Result、Snapshot 或 Domain Published Language 协调上下文。
- Adapter 使用动态 `Map<String, Object>` 接收外部 MCP/LLM/JSON 协议，但必须尽快包装成具名对象。

### 6.2 禁止

- Domain/Application 依赖 Trigger、Infrastructure、Spring、API DTO 或具体 JSON 框架。
- Controller/Facade 直接依赖 Domain Repository。
- 一个上下文直接修改另一个上下文的数据库表或 Aggregate 内部状态。
- 用裸 `Map<String, Object>` 作为新 Application Port 的默认返回协议。
- 通过共享数据库实体规避 Context Map。
- Work Session、Tool Provider 与 Repair 流程必须遵守 ChangePackage 审批、VERIFIED Repair Workspace / 受控代码与构建证据、Landing authority 与 fencing。
- 为兼容历史测试重新引入字段注入、无参可变 Service 或反射写依赖。

### 6.3 可执行 Context Map 基线

当前 Context Map 已覆盖全部已声明 Domain/Application 业务包，不再是“首批渐进矩阵”。以下门禁共同构成权威可执行约束：

- `BoundedContextDependencyArchitectureTest`：逐上下文限制 Domain/Application 可引用的 Domain 语言，并仅允许精确登记的跨上下文 Published Language；
- `ApplicationCrossContextServiceDependencyArchitectureTest`：禁止跨上下文注入具体 `ApplicationService`、`QueryService`、`UseCase`、`ProcessManager` 和 `Coordinator`；
- `CrossContextApplicationServiceDependencyArchitectureTest`：对全部 Application→Application 跨上下文 import 采用精确白名单，新增关系必须先明确上游/下游、所有权和契约性质；
- `DynamicApplicationPortBoundaryArchitectureTest`：稳定身份、状态、权限、风险、版本/hash 和编排结果不得重新退化为裸 Map Port。

```text
Project Workspace              → Project / Source / Shared
Agent Definition & Capability  → AgentDefinition / Agent / Shared
Work Session & Investigation   → WorkSession / Runtime / Analysis / Investigation /
                                 Intent / ChatSession / Shared
Controlled Change              → ChangePackage / Shared
Execution Governance           → Execution / ToolExecution / McpExecution / MCP /
                                 Toolset / Shared
Evidence & Trust               → Evidence / Audit / Shared
Knowledge & RAG                → Knowledge / RagEval / Shared
Skill Lifecycle                → Skill / Shared
Memory                         → Memory / Shared
Channel                        → Channel / Shared
Alert & Incident               → Alert / Incident / Statistics / AgentEval /
                                 ModelPolicy / Shared
Code Repair                    → Repair / Shared
Security Identity              → Security / Shared
Shared                         → Shared only
```

Application 包按同一限界上下文分组；同组可以内部协作，跨组只能使用窄 Port、不可变 Command/Result/Snapshot 或经评审的 Published Language。不得通过扩大整包允许集合、共享 Repository Entity 或把具体编排服务标记为“公共服务”规避门禁。

## 7. 动态协议例外

以下内容天然具有开放 Schema，可以保留动态属性：

- CQRS 查询/read-model 投影；
- MCP remote tool schema 和原始工具结果；
- LLM structured output 的未知扩展字段；
- 外部 IM/Git/监控平台 metadata；
- 审计附加 metadata；
- 用户自定义 Tool 参数；
- credential/schema/permission enrichment；
- authored changes/artifacts/evidence 等开放内容。

例外必须满足：

1. 新 Application Port 默认返回具名 Command/Result/Snapshot；确需裸 Map/List<Map> 返回时，必须逐文件登记到 `DynamicApplicationPortBoundaryArchitectureTest` 并标明 `CQRS_READ_MODEL`、`OPEN_PROTOCOL_RESULT` 等类别；
2. 状态、决策、标识、权限、风险等级、版本/hash、批处理 outcome 等稳定字段必须强类型化；
3. 动态字段必须做防御性复制、大小限制、脱敏和 canonical 化；
4. Domain Policy 不得散落读取不受控字符串 key；Application 不得从已登记协议 Map 反解析稳定字段推进状态机；
5. Skill authoring、similarity、effect metrics 和 publication 已证明会驱动候选准入、晋级或回滚，因此必须使用 typed Published Language，不属于动态协议例外。

## 8. Security 技术边界

`trigger.application.security.AdminAuthService` 仍负责 JWT/BCrypt 等认证协议实现并受 Secret/DLP 约束，但它不再拥有 Domain Repository 例外：

- 账号领域语言统一使用 `domain.security.AdminUserAccount`；
- 账号持久化通过 `application.security.AdminUserCatalogPort` 进入 Infrastructure；
- JWT revocation 通过 `application.security.JwtRevocationPort` 进入 Infrastructure；
- 全局 DDD 门禁不再为 `AdminAuthService` 特赦直接 Repository 依赖；
- Secret 校验和传输安全仍必须保持 fail-closed，DDD 清理不得弱化安全规则。

## 9. 新功能落位决策

新增能力按以下顺序判断：

```text
它属于哪个业务语言和状态生命周期？
→ 哪个限界上下文拥有该事实？
→ 聚合/Policy 负责什么不变量？
→ Application Use Case 负责什么编排？
→ 哪些外部能力需要窄 Port？
→ Adapter 如何隔离框架和动态协议？
→ Controller/Facade 是否只做 ACL 与协议映射？
→ 需要新增什么架构门禁？
```

无法回答“谁拥有状态”和“失败后由谁恢复”的功能，不得直接加入 Trigger Service。

## 10. Workflow、Tool 与 Skill 最终所有权

### 10.1 Workflow Definition 与 Runtime 分离

- **Agent Definition & Capability** 拥有 typed Workflow Definition、Node/Edge/Resource Reference、Rule AST、结构编译流水线、Definition Version 和 definition hash；
- **Work Session & Investigation** 拥有 Runtime Binding、Bound Workflow Execution Plan、plan hash、Durable Run State、Node Attempt、Route/Loop/Wait/Approval、Checkpoint 和恢复判定；
- Definition 编译结果只能通过不可变 Published Language 进入 Runtime Binding；运行时不得回写 Definition Current Pointer，也不得把 Spring `ChatModel`、`ToolCallback`、客户端或 Secret 写入 Bound Plan；
- 恢复必须同时校验 definition hash、plan hash 和 context bundle hash，禁止以“最新配置”替换原运行快照。

### 10.2 统一 Tool Execution 主链

Workflow Tool Node、Skill Behavior Replay 和普通 Runtime Tool Call 必须统一进入：

```text
Bound Tool/Resource Reference
→ Application Tool Execution Port
→ ToolExecutionApplicationService
→ Local/MCP Dispatch Handler
→ ToolResult + Evidence + Audit
```

- Workflow 与 Replay 只能使用 `PRE_APPROVAL_WORKFLOW`；
- 非只读调用只能生成 ChangePackage proposal，不能获得 Landing authority；
- Workflow 幂等键由 Durable Run 的 `runId:nodeId:attempt:toolCallIndex` 派生，调用方不得自行伪造；
- `APPROVED_LANDING` 仍只属于受批准 ChangePackage 的 Landing 流程。

### 10.3 Skill Lifecycle 最终边界

**Skill Lifecycle** 拥有以下状态和策略：

- 正交 Governance State、Legacy FROZEN fail-closed 分类；
- Defect Diagnosis、Optimization Memory 和最多三轮的 Optimization Run；
- Hint Selection、2–4 Candidate Authoring、Behavior Replay、Hidden/Mutation Eval、Verifier Version 和 Candidate Tournament；
- Routing Confusion Graph、低 margin 处置和 Progressive Package Budget；
- CREATE/PATCH/MERGE/SPLIT/COMPRESS/DEPRECATE/PRUNE/REVIVE/ROLLBACK Proposal、Decision、Retention State 和 Lineage。

Optimization Memory、Hidden Eval 和 Mutation Case 只服务 Skill 优化/评测，不得进入普通 Conversation Memory、Runtime Context Bundle 或 Authoring 输入。`PURGE_ELIGIBLE` 只是保留状态，不代表允许物理删除。

### 10.4 Migration 不是业务上下文

阶段 626 的 Platform Capability Migration 是 Application 层的受控兼容编排，不新增限界上下文，也不拥有业务事实。它只负责：

- 检查 dual-read 与 new-write 是否就绪；
- dry-run、受限批次 backfill、重复执行和结果 hash；
- 显式区分 `ALREADY_CURRENT`、`BACKFILLED`、`MANUAL_REVIEW_REQUIRED` 与 `FAILED`；
- 保留旧列/旧入口的兼容读取，不执行 DROP、TRUNCATE 或物理删除。

Skill Governance 的旧 `FROZEN` 行回填后必须保持 `LOCKED + QUARANTINED + LEGACY_UNCLASSIFIED`，直到人工完成分类；迁移不得把它自动恢复为可执行状态。
