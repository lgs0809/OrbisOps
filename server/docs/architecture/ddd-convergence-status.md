# OrbisOps DDD Convergence Status

更新时间：2026-08-15

## 1. 结论

OrbisOps 当前已经是一个成立的 **DDD + Hexagonal Modular Monolith**，并不是“只有目录长得像 DDD”。核心依赖方向、Domain/Application 框架隔离、Bounded Context Context Map、跨上下文 Application 依赖、动态协议边界和生产执行权限都已经有可执行门禁。

但当前仍不是最终形态。剩余问题主要不是 Domain 内核错误，而是历史演进留下的 **ownership convergence debt**：一部分旧业务所有权仍停留在兼容包中，一部分 Application orchestration 仍然落在 Trigger 技术层。

当前不存在必须阻止源码公开发布的 P0 DDD 问题；存在两个应继续收敛的 P1 架构债，以及若干 P2 可维护性问题。

## 2. 已经成立的 DDD 基线

### 2.1 依赖方向

当前主方向为：

```text
Domain <- Application <- Trigger / Infrastructure <- Composition Root
```

并由 `DddLayerDependencyArchitectureTest` 执行约束：

- Domain 不依赖 Application、Trigger、Infrastructure、API；
- Domain 不依赖 Spring、Fastjson、Jackson；
- Application 不依赖 Trigger、Infrastructure、API DTO；
- Application 不依赖 Spring、Fastjson、Jackson；
- Trigger 使用构造器注入，Facade/Application Service 不直接绕过 Application Port 访问 Repository（受保护认证兼容边界除外）。

### 2.2 战略 Context Map

`strategic-ddd-context-map.md` 已定义以下业务所有权：

```text
Core Domain
├── Work Session & Investigation
├── Controlled Change
├── Execution Governance
└── Evidence & Trust

Supporting Domain
├── Project Workspace
├── Agent Definition & Capability
├── Knowledge & RAG
├── Skill Lifecycle
├── Memory
├── Channel
├── Alert & Incident
└── Code Repair

Generic Domain
├── Security Identity
├── Model Policy
├── Statistics / Read Model
└── Canonical JSON / Hashing
```

`BoundedContextDependencyArchitectureTest` 将该 Context Map 转成了可执行 import matrix，而不是只依赖文档约定。

### 2.3 跨上下文调用

现有门禁已经禁止：

- Application Context 之间任意注入具体 `ApplicationService` / `QueryService` / `UseCase` / `ProcessManager` / `Coordinator`；
- 通过扩大 package allow-list 绕过 Context Map；
- 新 Application Port 将稳定身份、状态、权限、版本、hash、风险等级和编排结果退化成裸 `Map<String, Object>`；
- Domain/Application 直接消费 MCP SDK、HTTP DTO、JDBC/MyBatis 实现或模型 SDK 对象。

### 2.4 聚合和状态机

核心状态不是 Controller DTO 或数据库行直接驱动：

- ChangePackage 有 Current / Version / Approval / Event / Landing Operation 等独立事实与状态跃迁策略；
- WorkSessionRun、ChatSession、TaskContext、RuntimeContextBundle 是独立一致性边界；
- Definition Version 与 Skill Package 为不可变版本，Current Pointer 采用显式版本/hash/CAS 语义；
- Tool Result、Evidence、Trusted Proof 以不可变/append-oriented 事实参与可信执行；
- Channel/Alert/Tool execution 使用 durable outbox、lease、idempotency、reconciliation，而不是假设远端副作用和数据库事务原子提交。

这部分已经具备较强的战术 DDD 特征。

## 3. P1：`domain.agent` 仍是 Legacy Containment Zone

### 3.1 问题

`domain.agent` 是早期“大 Agent 上下文”遗留。RAG ownership 已在本轮迁出，目前剩余内容主要是：

- Agent/model compatibility records；
- Admin user / JWT repository compatibility types；
- Task schedule / execution compatibility types。

当前战略 Context Map 已经分别定义：

- RAG -> **Knowledge & RAG**；
- Admin identity -> **Security Identity**；
- Workflow/Agent definition -> **Agent Definition & Capability**。

因此 `domain.agent` 仍存在真实的业务所有权重叠，但 RAG 与 Security Identity 两部分已经完成收敛。

### 3.2 已完成：RAG ownership 迁移

RAG 的 Domain model、repository port、value object 和 domain service 已整体迁入：

```text
domain.knowledge.rag.model
domain.knowledge.rag.repository
domain.knowledge.rag.service
```

`application.rag`、`application.knowledge`、Infrastructure repository、Trigger parser/vector/BM25/multimodal adapter 以及相关测试全部改为消费 Knowledge & RAG 自己的领域语言。原有 `BoundedContextDependencyArchitectureTest` 中针对 `domain.agent.*Rag*` 的兼容 allow-list 已完全删除。

迁移后验证：

```text
旧 domain.agent RAG Java 引用: 0
Architecture Tests:            793 / 793 PASS
RAG-related Tests:             390 / 390 PASS
```

### 3.3 已完成：Security Identity ownership 迁移

账号和认证边界已从旧 `domain.agent` 中拆出：

```text
AdminUserAccount / AdminUserRole
    -> domain.security

AdminUserCatalogPort / JwtRevocationPort
    -> application.security

AdminUserRepository / JwtRevocationRepository
    -> infrastructure adapter
```

旧 `AdminUserRecord`、`IAdminUserRepository`、`IJwtRevocationRepository` 已删除。Channel 与 Authentication Adapter 都消费 typed `AdminUserAccount`；`AdminAuthService` 不再拥有直接 Domain Repository 例外，全局 DDD 门禁的认证特赦也已删除。

### 3.4 当前剩余遗留

`domain.agent` 当前主要只剩 Agent/model compatibility records 与 Task schedule/execution compatibility types。继续采用 Strangler 策略：

1. `domain.agent` 继续作为 **legacy containment zone**；
2. `LegacyAgentDomainContainmentArchitectureTest` 禁止其他 Domain Context 把它扩展成 Shared Kernel；
3. RAG 与 Security Identity ownership 已迁出；
4. 下一步迁移 Task / Model / legacy Agent configuration ownership；
5. 后续只允许依赖面缩小，不允许扩大。

### 3.5 收敛顺序

建议按以下顺序迁出：

```text
✓ RAG model/repository/policy
    -> domain.knowledge.rag / Knowledge & RAG ports

✓ AdminUser / JWT revocation compatibility
    -> domain.security + security application ports

→ Task/model legacy records
    -> Agent Definition / Model Policy / Schedule 对应所有者

最后删除 domain.agent compatibility package
```

每一步都应保持行为测试和 architecture tests 全绿，不做大爆炸式 package migration。

## 4. P1：Trigger 仍然偏胖

### 4.1 正确的 Trigger 职责

Trigger 应主要负责：

- HTTP/SSE/Channel/Scheduler inbound adapter；
- Spring wiring / configuration；
- MCP / model SDK / protocol ACL；
- 外部动态 JSON/schema 到内部 typed contract 的转换；
- Application Port 的 outbound adapter；
- runtime framework integration。

### 4.2 当前剩余问题

`trigger.ops` 中仍有部分代码承担了明显的 Application orchestration，例如：

```text
trigger.ops.change.OpsChangePackagePreparationService
trigger.ops.OpsInvestigationLoopService
trigger.ops.OpsInvestigationFollowUpService
trigger.ops.OpsInvestigationRetryService
trigger.ops.skill.OpsSkillEvolutionPipelineService
trigger.ops.runtime.*Coordinator
```

其中有些类本质上是 framework adapter，但有些类同时：

1. 调用多个 Application Service；
2. 决定业务阶段顺序；
3. 组合业务 decision；
4. 承担恢复/编排语义；
5. 又持有 Spring/ObjectProvider/外部协议细节。

这会形成“Application layer 已经存在，但最后一公里业务编排仍堆在 Trigger”的胖适配层。

### 4.3 目标形态

目标不是把所有 `trigger.ops.runtime` 机械移动到 Application。正确拆法是：

```text
Domain
  纯业务 invariant / policy / decision

Application
  use case / process manager / orchestration / recovery decision
  + narrow outbound ports

Trigger / Infrastructure
  Spring / SDK / MCP / HTTP / filesystem / process / DB protocol adapter
```

例如 ChangePackage Prepare 最终应收敛为：

```text
Trigger request mapper
    -> PrepareChangePackageUseCase
        -> domain ChangePackagePreparationPolicy
        -> Project/Agent/Evidence narrow ports
    <- typed PreparationPlan
```

而不是让 Trigger Service 自己同时编排 Project ApplicationService、Agent Gateway、Evidence Store、Repair Service 和 Domain Policy。

### 4.4 收敛原则

- 优先迁移 **业务编排**，不是迁移技术 client；
- 一个类只要仍强依赖 Spring/MCP SDK/HTTP/process/file system，就应优先作为 Adapter 留在外层；
- 一个类如果主要决定“先做什么、失败后怎么办、下一状态是什么”，应优先进入 Application/Domain；
- 禁止为了目录整齐创建一套与现有 Use Case 平行的第二条调用链。

## 5. P2：Maven 是 Layer-first，而不是 Context-first

当前 Maven 模块按宏观技术层拆分：

```text
orbisops-domain
orbisops-application
orbisops-infrastructure
orbisops-trigger
...
```

Bounded Context 则由 package + architecture tests 隔离。

这是一种成立的 Modular Monolith 方案，并非 DDD 错误；优点是 build 简单、跨上下文 Application Port 编排成本低。

代价是：

- Java compiler 本身不能像独立 Maven context module 那样阻止所有跨 Context import；
- Context 隔离依赖 architecture tests；
- `domain/application/trigger` 三个模块会随着产品增长越来越大。

当前阶段 **不建议立刻拆成十几个 Maven bounded-context module**。只有当团队规模、独立发布、构建时间或 Context owner 明显需要物理隔离时，再把四个核心域优先模块化。

## 6. P2：兼容 Facade / 命名仍有历史痕迹

### 6.1 Trigger compatibility policy wrappers

例如部分 `Ops*Policy` 只是 Spring/Trigger facade，真实 invariant 已经位于 Domain Policy。它们是过渡层，不应继续承载新业务规则。

### 6.2 `domain.agent.adapter.repository`

这里实际存放的是 Domain repository **ports**，但路径仍叫 `adapter.repository`。从 Hexagonal 术语看容易误导：adapter 应在外层，Domain 里应该叫 `port` / `repository`。

由于它属于待删除的 legacy `domain.agent`，不建议只为重命名再制造一次大规模 churn；应随 Legacy Agent Context 迁出一起消失。

## 7. P3：动态协议仍然较多，但边界基本正确

Trigger/runtime/MCP/LLM 层仍大量使用 `Map<String, Object>`，这是开放 schema 系统无法完全避免的。

真正的判断标准不是“有没有 Map”，而是：

- Map 是否停留在外部协议/ACL；
- 稳定状态是否尽快投影为 typed object；
- Domain Policy 是否依赖魔法字符串 key；
- Application Port 是否用 Map 传递稳定业务契约。

当前已有 `DynamicApplicationPortBoundaryArchitectureTest` 保护稳定 Application contract，因此这里属于持续清理项，不是当前 DDD 阻断项。

## 8. 推荐收敛路线

```text
Phase 1  Freeze
  ✓ 修正 Context Map 陈旧 domain.intent 描述
  ✓ 删除已不需要的 legacy RAG Published Language allow-list
  ✓ 禁止其他 Domain Context 新依赖 domain.agent

Phase 2  Ownership
  ✓ RAG ownership 迁入 Knowledge & RAG
  → Security compatibility records 迁入 Security Identity
  → 清空 legacy domain.agent

Phase 3  Applicationize
  → ChangePackage preparation orchestration 从 Trigger 下沉 Application
  → Investigation loop / retry / follow-up orchestration 下沉 Application
  → Skill pipeline orchestration 下沉 Application
  → Runtime 中“业务恢复决策”与“框架执行器”分离

Phase 4  Physical modularity（按需）
  → 若规模继续增长，优先物理拆分四个 Core Domain
  → Supporting Context 保持模块化单体，直到独立生命周期有真实收益
```

## 9. 发布判断

从 GitHub 源码发布角度：**GO**。

原因是当前剩余问题属于可控的 ownership/convergence debt，而不是依赖倒置失效、Domain 被框架污染、跨 Context 任意访问 Repository、生产权限绕过或状态机散落在 Controller 这一类结构性 P0 问题。

发布后应继续按照本文件的 Phase 2 / Phase 3 收敛，而不是宣称 DDD 已经“100% 完成”。
