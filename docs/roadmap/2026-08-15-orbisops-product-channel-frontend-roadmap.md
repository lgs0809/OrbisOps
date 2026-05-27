# OrbisOps 后续产品、Channel 与前端重构方案

> 日期：2026-08-15  
> 状态：后续实施权威方案（Proposal）  
> 适用仓库：OrbisOps monorepo  
> 范围：OrbisOps 产品信息架构、前端工程重构、多 IM Channel、Channel Approval、Incident/Diagnosis/Change 连续工作台  
> 不包含：重新设计 ReAct Runtime、重新设计 ChangePackage 权威模型、推翻现有 DDD、引入第二套生产变更权限体系

---

## 1. 背景与当前判断

OrbisOps 当前后端治理内核已经相对成熟：

- 默认 Chat / Diagnosis 使用 Project Default ReAct；
- 显式选择专项 Agent 使用固定 Workflow；
- 生产调查与生产变更权限分离；
- `ChangePackage -> Approval -> Landing -> Verification` 已形成受治理生产变更主链；
- MCP / Skill / Knowledge / Code Workspace 等能力通过 Runtime capability 暴露；
- Domain / Application / Infrastructure / Trigger 边界已完成一轮较彻底的 DDD 收敛；
- Channel 已具备 Channel Domain、入站队列、出站 Outbox、身份绑定、ReAct / Workflow 路由、重试/恢复、审计和 Generic Webhook。

当前主要问题已经不是“功能是否存在”，而是：

1. **产品信息架构仍偏能力平铺**：Incident、Chat、Execution、Workflow、Schedule、Alert、Model、MCP、RAG、Skill、Memory、Channel 等对象都比较容易成为一等入口。
2. **用户任务链没有完全成为 UI 主角**：真实用户关心的是“问题发生 -> 调查 -> 形成变更 -> 审批 -> 执行 -> 验证恢复”，而不是在多个技术模块之间切换。
3. **前端工程边界落后于后端 DDD**：存在超大 Page、God Service、重复路由/导航配置和大量页面级 server-state 管理。
4. **Channel 只有框架，没有原生 Provider 矩阵**：当前真正实现的 Provider 只有 `GENERIC_WEBHOOK`，飞书、企业微信等仍需要外部 Bridge。
5. **Channel 尚未成为 OrbisOps 治理闭环的一等交互面**：还不能在飞书/企业微信等渠道中安全完成 Incident 进度查看、ChangePackage 审批、Landing/Verification 结果闭环。

因此下一阶段目标不是继续堆积功能，而是把 OrbisOps 收敛成真正的产品：

> **Investigate freely. Change deliberately.**  
> 用户处理的是 Work；Agent/MCP/RAG/Skill/Channel 是实现 Work 的平台能力，而不是反过来成为产品导航的主人。

---

## 2. 外部参考：reference channel implementation Channel 能力边界

reference channel implementation 当前公开版本把 Channel 作为 Provider SPI，并已经接入 8 个 IM 平台：

- DingTalk / 钉钉
- Feishu / Lark / 飞书
- WeCom / 企业微信
- WeChat / 微信
- Telegram
- Discord
- QQ
- Slack

公开资料中已经体现的关键能力包括：

- 同一个 Agent 能通过不同 Channel 工作；
- 不同 Channel 使用 WebSocket、Stream、Long Polling、Webhook、Socket Mode 等不同传输协议；
- Feishu 已经是一等 Channel，支持 interactive / approval / streaming cards；
- Feishu / WeCom 的卡片按钮已经可以闭环 Workflow Approval；
- 长任务会向 IM 持续反馈进度；
- Channel Adapter 只负责 Channel 协议和交互，不应该复制 Agent Runtime；
- Channel 配置有 preflight / health / status / 生命周期管理；
- 身份、会话、附件和异步结果会在 Channel 层做适配。

OrbisOps 应借鉴这些**成熟的 Channel 工程模式**，但不能照抄 reference channel implementation 的权限语义。

OrbisOps 自己已经有更强、更明确的生产治理链：

```text
Observe / Investigate
        |
        v
ChangePackage
        |
        v
Approval
        |
        v
Trusted Landing
        |
        v
Verification
```

因此必须坚持：

> **Channel 是交互界面，不是审批事实源，不是 Runtime 权限源，也不是生产变更旁路。**

飞书/企微上的“Approve”按钮只能发出一个经身份验证的 Approval Command；真正的状态、权限、乐观锁、审批规则、ChangePackage 版本、Landing Authority 仍由 OrbisOps 后端权威模型决定。

---

# Part A：产品信息架构与核心工作流重构

## 3. 产品心智模型

后续 OrbisOps 对用户只需要强调三个主要概念：

```text
Work
解决正在发生的问题

Automate
把重复工作自动化

Platform
给 Agent 提供能力、资源和治理
```

项目是 Work 和 Platform 的作用域，因此保持一等入口。

推荐最终一级导航：

```text
Home

Work
  Incidents
  Diagnosis
  Changes

Automations
  Workflows
  Inspections
  Alert Triggers

Projects

Platform
  Integrations / Channels
  Models
  Tools / MCP
  Knowledge
  Skills
  Execution Targets
  Governance
  Advanced
```

其中：

- `Memory` 不再作为普通管理员高频一级对象，进入 `Platform -> Advanced`；
- `Skill Evolver` 进入 `Platform -> Advanced / Governance`；
- `Tool Routing Observability` 进入 `Platform -> Governance / Advanced`；
- `Audit` 不作为独立产品世界：
  - Incident / Change / Channel / Workflow 的审计优先出现在对象 Timeline；
  - 全局审计进入 `Platform -> Governance`；
- `Channel` 归入 `Platform -> Integrations`，因为它是交互/通知接入能力，而不是一套独立 Automation Runtime。

### 3.1 保留独立 Chat，但降低“模块感”

Chat 仍然必须存在，因为用户需要：

- 临时问题调查；
- 尚未形成 Incident 的探索；
- 项目默认 ReAct 的直接入口。

但产品上应表现为 `Diagnosis`，并且可以随时：

- 关联已有 Incident；
- 从诊断创建 Incident；
- 从诊断形成 ChangePackage；
- 打开当前 Run / Evidence / Tool Trace。

不要把“Chat 页面”和“Incident 页面”做成两个互不相干的世界。

---

## 4. 核心旗舰体验：Incident Workspace

### 4.1 为什么 Incident 应成为 Work 的核心聚合

真实运维任务不是“使用 Chat”，而是“解决一个问题”。

最终主路径应统一为：

```text
Alert / Inspection / Human Question / Channel
                    |
                    v
                 Incident
                    |
        +-----------+-----------+
        |           |           |
   Investigation  Evidence    Timeline
        |
        v
   ChangePackage (optional)
        |
        v
      Approval
        |
        v
      Landing
        |
        v
   Verification
        |
        v
      Resolved
```

### 4.2 Incident Workspace 页面结构

建议：

```text
Incident / INC-20260815-001

[Severity] [Status] [Project] [Owner] [Service]

Overview | Investigation | Evidence | Change | Timeline
```

#### Overview

- 当前状态；
- 影响范围；
- 服务 / 环境；
- 当前责任人；
- 最近结论；
- 下一步动作；
- 是否需要人类决定。

#### Investigation

直接承载当前/历史 Diagnosis Run：

- ReAct 运行状态；
- 当前阶段；
- 已查询数据源；
- Grounded Conclusion；
- 已验证事实 / 推断 / 未知项；
- Follow-up / Clarification；
- 重新调查入口。

#### Evidence

统一 Evidence Explorer：

- Prometheus；
- Logs；
- MySQL / DB；
- Elasticsearch；
- Code；
- RAG / Knowledge；
- MCP Tool result；
- `resultId / outputHash / observedAt / provenance`。

#### Change

没有 ChangePackage：

- 展示“当前调查尚未形成生产变更”；
- 可以在权限允许时生成 ChangePackage。

存在 ChangePackage：

- Why；
- What；
- Where；
- Risk；
- Blast Radius；
- Evidence；
- Rollback；
- Verification；
- Approval state；
- Landing state。

#### Timeline

统一收敛：

- Incident events；
- Diagnosis runs；
- Channel inbound/outbound；
- ChangePackage versions；
- Approval；
- Landing；
- Verification；
- Human interventions；
- Skill evolution evidence（如果发生）。

### 4.3 URL 与上下文

建议稳定使用对象 URL：

```text
/work/incidents/:incidentId
/work/incidents/:incidentId/investigation
/work/incidents/:incidentId/evidence
/work/incidents/:incidentId/change
/work/incidents/:incidentId/timeline
```

Chat/Channel/Alert 打开的都应该是相同 Incident Context，而不是创建另外一套详情页状态。

---

## 5. ChangePackage Approval Cockpit

ChangePackage 是 OrbisOps 区别于普通 Agent 平台最重要的产品资产之一，前端应明显强化。

审批页默认不要让人先读 JSON，而是回答七个问题：

```text
WHY?
为什么要改？

WHAT?
准备改什么？

WHERE?
目标环境、资源、服务是什么？

EVIDENCE?
依据是什么？

RISK?
风险与 blast radius 是什么？

ROLLBACK?
如何撤销？

VERIFY?
什么条件代表恢复成功？
```

主界面推荐：

```text
ChangePackage CP-xxx
HIGH RISK · PRODUCTION

Reason
Evidence
Planned Changes
Blast Radius
Rollback
Verification

[Reject] [Request Revision] [Approve]
```

高级数据统一放入：

```text
Advanced Evidence
  raw payload
  hashes
  result ids
  proof bundle
  adapter metadata
  protocol metadata
```

### 5.1 Approval 与 Clarification 分离

必须继续坚持：

- Clarification = 信息不足，需要用户补充；
- Approval = 对明确 ChangePackage 作授权决策。

Channel 以后也不能把这两种交互混成一个“确认”按钮。

---

## 6. Dashboard：从统计看板改成 Attention & Action Center

首页只回答：

> **现在最值得我处理什么？**

推荐顺序：

1. Action Required；
2. 当前 P0/P1 Incident；
3. Pending Approval / Pending Human Decision；
4. Landing Failed / Verification Failed；
5. Project Readiness Blocker；
6. Recent Activity；
7. Platform Health。

不优先展示 vanity metrics：

- Agent 数；
- MCP 数；
- 总对话次数；
- 今日 Token 数。

这些可以作为 Platform Analytics，而不是 Home 主角。

---

## 7. Project Workspace 继续沿用“产品页 + Advanced”模式

当前 Project Product Workspace 的方向正确，应扩展为：

```text
Project / Payment Platform

Overview
Connections
Knowledge & Skills
Code
Execution
Members
Settings
```

### Connections

统一承载：

- Prometheus；
- MySQL；
- Elasticsearch；
- Redis；
- Kafka / MQ；
- Generic MCP；
- generated MCP。

### Knowledge & Skills

- Knowledge Bases；
- Project Skills；
- Skill binding；
- Memory policy（Advanced）。

### Code

- Repositories；
- Code Workspace MCP；
- LSP；
- workspace health。

### Execution

- production targets；
- Landing adapters；
- rollback capability；
- verification capability。

### Members

- project members；
- role；
- Channel identity mapping 状态。

不要继续把所有配置堆入一个超级 `project-workspace.tsx`。

---

## 8. Workflow / Agent Builder：Basic + Advanced Progressive Disclosure

默认用户看到的是业务级配置：

```text
Agent Node

Instruction
Model
Capabilities
Behavior: DIRECT / LLM / REACT
```

资源选择使用人类可理解的对象：

- Production Metrics；
- Logs；
- Databases；
- Code；
- Knowledge；
- Skills。

Advanced 才展示：

- MCP IDs；
- Skill IDs；
- raw runtime metadata；
- exact workflow version/hash；
- Router expression；
- Loop guard；
- JSON config；
- low-level Direct Action config。

目标不是删能力，而是：

> 把 Runtime schema 翻译成人类操作模型。

---

# Part B：前端工程重构

## 9. 前端目标架构

推荐把当前按 `pages/services/components` 为主的结构逐步迁成 feature-first：

```text
web/src/
  app/
    routes/
    navigation/
    providers/
    auth/

  features/
    home/
    incidents/
    diagnosis/
    changes/
    automations/
    agents/
    projects/
    channels/
    models/
    tools/
    knowledge/
    skills/
    governance/

  shared/
    api/
    ui/
    hooks/
    lib/
    types/
    styles/
```

### 9.1 禁止继续扩大 God Page

重点拆分：

- `agent-config.tsx`；
- `project-workspace.tsx`；
- `rag-order-management.tsx`；
- `chat-workspace.tsx`；
- `channel-management.tsx`；
- `change-package-center.tsx`。

Page 只负责：

- route params；
- feature composition；
- 页面布局；
- 轻量 orchestration。

复杂规则进入：

- feature model；
- hook；
- query/mutation；
- view model；
- pure TypeScript policy。

### 9.2 拆 `ops-admin-service.ts`

最终不允许继续存在一个跨所有业务域的 Admin God Service。

示例：

```text
features/incidents/api/incident-api.ts
features/diagnosis/api/diagnosis-api.ts
features/changes/api/change-api.ts
features/agents/api/agent-api.ts
features/channels/api/channel-api.ts
features/projects/api/project-api.ts
```

Shared API 只保留：

- base client；
- auth interceptor；
- error envelope；
- request id；
- common pagination。

---

## 10. TanStack Query 成为 Server State 单一方案

逐步停止以下模式：

```text
useState(loading)
useState(error)
useEffect(load)
manual refresh
mutation 后手工 reload
```

改成：

```text
useIncidentQuery()
useIncidentTimelineQuery()
useChangePackageQuery()
useChannelListQuery()
useProjectReadinessQuery()

useApproveChangeMutation()
useRunDiagnosisMutation()
useCreateChannelMutation()
```

统一 query key：

```text
['projects']
['project', projectId]
['incidents', projectId, filters]
['incident', incidentId]
['incident', incidentId, 'timeline']
['changes', projectId]
['channel', projectId, channelId]
```

Mutation 后通过精确 invalidation 更新，不允许全页面 reload。

---

## 11. Route Metadata 成为 Router / Sidebar / Permission 单一事实源

当前 Route 与 Sidebar 分开维护，长期必然漂移。

后续统一：

```ts
{
  id: 'incidents',
  path: '/work/incidents',
  roles: ['ADMIN', 'USER'],
  navigation: {
    group: 'work',
    label: '事件',
    icon: ...,
    order: 10,
  },
  breadcrumb: ...,
  element: ...,
}
```

以下都从同一份 metadata 派生：

- Router；
- Sidebar；
- Breadcrumb；
- Page Title；
- RBAC visibility；
- Command Palette；
- Global Search actions。

Legacy route 继续保留 redirect 一段时间，不直接破坏 bookmark。

---

## 12. Design System 与 OrbisOps Authority Visual Language

不再以“普通蓝色企业管理后台”为最终视觉目标。

建立 OrbisOps 自己的语义 token：

```text
OBSERVE            neutral / blue
EVIDENCE           cyan
REASONING          violet
PROPOSED_CHANGE    amber
AWAITING_APPROVAL  orange
APPROVED           teal
LANDING            yellow
VERIFIED           green
BLOCKED / FAILED   red
```

注意：颜色只是辅助信息，必须同时有 label/icon/status，不允许只靠颜色传达状态。

统一组件：

- `OpsPageShell`；
- `OpsPageHeader`；
- `OpsSectionCard`；
- `OpsStatusBadge`；
- `OpsAuthorityBadge`；
- `OpsTimeline`；
- `OpsEvidenceCard`；
- `OpsRiskBadge`；
- `OpsEmptyState`；
- `OpsDangerZone`；
- `OpsDetailDrawer`。

逐步删除各页面自己定义的重复 PageLayout / Panel / Header / Toolbar。

---

# Part C：多 Channel 平台方案

## 13. 支持范围

目标 Provider 矩阵：

| Provider | 首选接入方式 | 关键能力 | 首轮优先级 |
|---|---|---|---|
| Generic Webhook | HTTP Webhook | 通用桥接 / 自定义系统 | 已有，保留 |
| Feishu / Lark | WebSocket + Webhook fallback | DM/群聊、卡片、审批、附件、进度更新 | P0 |
| WeCom | 长连接 / Webhook | 群聊、回调、主动通知、审批交互 | P0 |
| DingTalk | Stream WebSocket + Webhook fallback | 群聊、AI Card、流式进度 | P1 |
| Slack | Socket Mode + Webhook/API | thread、Block Kit、审批按钮 | P1 |
| Telegram | Long Polling + optional Webhook | bot DM/group、inline keyboard、文件 | P2 |
| Discord | Gateway WebSocket | DM/guild、thread、button/component、附件 | P2 |
| QQ | WebSocket / callback | DM/群能力按平台限制降级 | P2 |
| WeChat | Official Account Secure Webhook + 客服消息 API | 文本私聊入站、回复窗口内文本发送 | P3 |

说明：

- 所有 Provider 最终都要接入；优先级只表示实施顺序，不表示最终范围删除。
- 不强行要求每个 Channel 具备完全相同的 Rich UI；统一能力模型 + capability fallback 即可。
- 不因为某个 Provider 不支持按钮，就在 Application 层写 Provider 分支。

---

## 14. Channel DDD / Hexagonal 边界

### 14.1 现状问题

当前 `OpsChannelAdapter`：

```java
String type();
void validateConfiguration(Map<String, Object> channel);
Map<String, Object> send(...);
```

适合 Generic Webhook，但不足以承载：

- inbound protocol parse / verify；
- WebSocket/long-poll lifecycle；
- rich cards；
- interactive callbacks；
- message update；
- typing / progress；
- attachments；
- Provider health/preflight；
- capability discovery。

而且 `Map<String,Object>` 会让 Provider schema 泄漏到运行时。

### 14.2 目标：Application 使用强类型 Channel Contract

建议 Application 层新增/演进为：

```text
ChannelProviderPort
ChannelInboundPort
ChannelOutboundPort
ChannelInteractiveActionPort
ChannelLifecyclePort
ChannelHealthPort
```

核心强类型模型：

```text
ChannelType
ChannelCapabilitySet
ChannelConnectionMode
ChannelInboundEnvelope
ChannelExternalPrincipal
ChannelConversationRef
ChannelMessageRef
ChannelOutboundMessage
ChannelRichContent
ChannelAttachment
ChannelInteractiveAction
ChannelDeliveryReceipt
ChannelHealthSnapshot
ChannelProviderConfiguration
```

Application/Domain 禁止返回/接收动态 `Map<String,Object>` 作为核心协议。

Provider-specific JSON 只允许存在于 Trigger/Infrastructure ACL 中。

---

## 15. Provider SPI

推荐 Trigger Adapter SPI：

```java
public interface ChannelProviderAdapter {
    ChannelType type();

    ChannelCapabilitySet capabilities();

    void validateConfiguration(ChannelProviderConfigView config);

    ChannelHealthSnapshot preflight(ChannelProviderConfigView config);

    ChannelInboundEnvelope parseInbound(ChannelInboundRequest request);

    ChannelDeliveryReceipt send(ChannelOutboundRequest request);

    default ChannelDeliveryReceipt update(ChannelMessageUpdate request) {
        throw new UnsupportedOperationException();
    }

    default ChannelInteractiveAction parseAction(ChannelActionRequest request) {
        throw new UnsupportedOperationException();
    }
}
```

对于需要常驻连接的 Provider，再通过：

```java
ChannelConnectionDriver
  start(channelId)
  stop(channelId)
  health(channelId)
```

处理：

- Feishu WS；
- WeCom 长连接；
- DingTalk Stream；
- Slack Socket Mode；
- Discord Gateway；
- Telegram Long Poll；
- QQ WebSocket。

WeChat 不进入常驻连接 Driver：官方公众号使用 Secure Webhook callback，由 Provider callback ingress 验签/解密后进入统一 Channel durable pipeline。

不要让 Application Service 自己管理 WebSocket thread。

---

## 16. Capability 模型：不要假设所有 Channel 都一样

建议统一 capability：

```text
TEXT
MARKDOWN
RICH_CARD
INTERACTIVE_BUTTON
MESSAGE_UPDATE
STREAMING_UPDATE
TYPING_INDICATOR
THREAD
GROUP
DM
IMAGE_IN
IMAGE_OUT
FILE_IN
FILE_OUT
AUDIO_IN
AUDIO_OUT
VIDEO_IN
VIDEO_OUT
PROACTIVE_PUSH
REACTION
```

每个 Provider 声明能力：

```text
Feishu -> RICH_CARD + INTERACTIVE_BUTTON + MESSAGE_UPDATE + GROUP + DM + FILE...
Slack  -> BLOCK/KIT equivalent + THREAD + INTERACTIVE_BUTTON...
Telegram -> INLINE_KEYBOARD + GROUP + DM...
QQ -> 按当前平台限制降级
```

Application 只表达“我要发送 Approval Request / Progress / Result”，Renderer 根据 capability 选择：

```text
Native Interactive Card
    -> Rich Markdown
        -> Plain Text + Web Approval Link
```

这是正确的 Progressive Enhancement。

---

## 17. Channel 消息统一模型

### 17.1 Inbound

Provider Adapter 统一产出：

```text
ChannelInboundEnvelope
  channelId
  provider
  externalMessageId
  externalConversationId
  externalThreadId?
  sender
    externalUserId
    displayName?
  messageType
  text?
  attachments[]
  mentions[]
  timestamp
  rawEventRef
```

### 17.2 Outbound

Application 输出语义消息，不输出 Feishu JSON：

```text
ChannelOutboundMessage
  kind
    CHAT_REPLY
    INCIDENT_NOTIFICATION
    RUN_PROGRESS
    CLARIFICATION
    APPROVAL_REQUEST
    APPROVAL_RESULT
    LANDING_PROGRESS
    VERIFICATION_RESULT
  title
  body
  facts[]
  actions[]
  attachments[]
  correlation
    incidentId
    runId
    changePackageId
    approvalId
```

Provider Renderer 再映射到：

- Feishu card；
- WeCom markdown/card；
- DingTalk AI Card；
- Slack Block Kit；
- Telegram inline keyboard；
- Discord components；
- QQ / WeChat fallback text。

---

## 18. Channel 身份与安全模型

### 18.1 修改当前“未绑定也可调查”的默认策略

OrbisOps 是生产运维系统，默认策略必须比普通聊天 Agent 更严格。

新增 `ChannelAccessPolicy`：

```text
DENY_UNKNOWN          默认
PAIRING               推荐
ALLOWLIST
OBSERVE_ONLY_UNKNOWN  高级显式 opt-in
```

默认：

```text
unknown sender
    -> 不进入 Project ReAct
    -> 返回 pairing / access request
```

只有管理员显式启用 `OBSERVE_ONLY_UNKNOWN` 时，才允许未知身份进入严格只读调查；且必须：

- 不得审批；
- 不得 Landing；
- 不得触发生产 mutation；
- 不得跨 project；
- 不得读取未显式授权敏感 Evidence；
- 必须写完整审计。

### 18.2 privileged action

以下操作必须存在有效 `ChannelIdentityMapping`：

- Approve / Reject ChangePackage；
- Workflow human approval；
- Landing resume；
- cancel / rollback 等控制动作；
- 任何未来高权限 channel action。

校验顺序：

```text
Provider signature / connection trust
        ->
Channel active
        ->
External sender identity mapping
        ->
Platform user active
        ->
Project membership
        ->
RBAC / Approval policy
        ->
Target object version/state
        ->
Command execute
```

Channel button 不能绕过任何一步。

### 18.3 防重放与幂等

必须同时使用：

- Provider event id / externalMessageId dedupe；
- action idempotency key；
- timestamp window；
- nonce/action token；
- optimistic version；
- approval current-state check；
- delivery idempotency key。

重复点击 Approve 必须得到“已经处理”的幂等结果，不能产生第二次审批记录。

---

## 19. ChangePackage / Workflow Approval 的 Channel 闭环

### 19.1 权威原则

```text
Channel Card
   |
   | button click
   v
Channel Action Callback
   |
   v
Resolve identity
   |
   v
ApprovalApplicationService
   |
   v
Authoritative DB state
```

**绝不：**

```text
Feishu click -> 直接 Landing
```

### 19.2 ChangePackage Approval Card

语义：

```text
PRODUCTION CHANGE REQUIRES APPROVAL

Project / Environment
Risk
Reason
Evidence summary
Planned changes
Blast radius
Rollback
Verification

[Reject] [Request Revision] [Approve]
```

按钮 payload 只包含短期 action token / opaque action id，不把 production target、tool args、secret 放在客户端卡片 payload 中。

### 19.3 Workflow Approval

Workflow human approval 可复用同一 `ChannelApprovalInteraction`，但审批对象必须明确：

```text
ApprovalSubjectType
  CHANGE_PACKAGE
  WORKFLOW_STEP
```

Clarification 仍是另一类 `ChannelInteractiveAction`。

---

## 20. Incident / Diagnosis 的 Channel 体验

### 20.1 用户从 Channel 发起

```text
@OrbisOps 支付成功率为什么下降？
    -> Channel inbound
    -> identity/project resolution
    -> Project Default ReAct
    -> Incident optional/autocreate policy
    -> Run
```

### 20.2 进度消息

不要每个 tool call 发一条刷屏消息。

统一成一个 `RUN_PROGRESS`，Provider 支持 message update 时更新原消息：

```text
Investigating...

✓ Metrics
✓ Logs
● Database
○ Code

Current finding:
Connection pool saturation suspected.
```

不支持 update 的 Provider：

- 只发送阶段节点；
- 做 debounce；
- 结束时发送最终结果。

### 20.3 结束

```text
Diagnosis completed

Root cause
Evidence
Impact
Recommended next step

[Open Incident]
[Prepare Change]
```

`Prepare Change` 仍然只是请求 OrbisOps 进入 ChangePackage preparation，不允许直接 mutation。

---

## 21. Provider 实施细节

### 21.1 Feishu / Lark（P0）

支持：

- WebSocket 长连接优先；
- Webhook fallback；
- DM / group；
- mention policy；
- interactive card；
- approval card；
- message update / progress card；
- text / image / file / audio/video 能力按 SDK/平台支持渐进接入；
- external user/open id -> OrbisOps identity mapping；
- card callback -> OrbisOps Approval Command。

Provider config 页面：

```text
App ID
App Secret -> credentialRef
Verification Token -> credentialRef
Encrypt Key -> credentialRef
Connection Mode
Group Mention Policy
Access Policy
Project
Inbound Execution
```

### 21.2 WeCom（P0）

支持：

- 长连接 / callback；
- DM / group 能力按企业微信机器人类型区分；
- reply-to-inbound 与 proactive push 分开建模；
- markdown/rich message；
- approval interaction；
- external UserId -> identity mapping；
- provider 限制下的 progress bubble / staged updates。

必须特别测试：

- 回调重复；
- 群聊 @；
- 主动推送限制；
- 超时 reply；
- 长任务结束后的异步结果。

### 21.3 DingTalk（P1）

- Stream WebSocket 首选；
- Webhook fallback；
- AI Card / markdown；
- group @；
- callback dedupe；
- approval card（平台能力允许时）；
- streaming/progress update。

### 21.4 Slack（P1）

- Socket Mode；
- channel/thread/DM；
- Block Kit；
- interactive action callback；
- file upload；
- thread correlation；
- Approval button。

### 21.5 Telegram（P2）

- Long Polling 默认；
- Webhook 可选；
- group / DM；
- inline keyboard；
- files/images；
- typing indicator；
- message edit 用于 progress；
- allowlist / pairing。

### 21.6 Discord（P2）

- Gateway WebSocket；
- guild / channel / DM；
- thread；
- component buttons；
- attachments；
- message edit；
- interaction signature / event idempotency。

### 21.7 QQ（P2）

- WebSocket / callback；
- 按 QQ Bot 当前可用 scope 提供 DM/group；
- 不支持的 rich interaction 自动 fallback；
- 图片/文件按 Provider capability 声明；
- 不在 Application 层伪造“所有 QQ 都支持按钮”。

### 21.8 WeChat（P3）

- 已按 **WeChat Official Account 官方 Secure Webhook** 实现，不使用个人微信号 QR 注入或非官方 Session Bridge；
- callback 使用 `msg_signature + timestamp + nonce + Encrypt` 校验，AES 安全模式解密后才进入 OrbisOps；
- 当前能力仅声明 `INBOUND / OUTBOUND / DIRECT_MESSAGES / WEBHOOK / REPLY_TO_INBOUND`；
- 仅接受文本私聊进入 Runtime；图片/语音/视频等当前安全 ACK 后忽略，不伪装已支持；
- 出站使用官方客服文本 API，并受微信平台自身发送窗口/权限限制，因此不声明无限制 `PROACTIVE_PUSH`；
- 不声明 Group、Attachment、Interactive Approval、Message Update；这些能力在 capability matrix 中明确 unsupported；
- App Secret、Callback Verification Token、EncodingAESKey 全部只保存 credential ref / env ref，原始 secret 不进入 Channel config；
- `preflight` 即使 API credential 校验成功，也只返回 `UNKNOWN / WECHAT_CALLBACK_EXTERNAL_VERIFICATION_REQUIRED`，必须有真实 callback / outbound 事实后才允许 readiness 收口；
- callback endpoint：`/api/v1/channels/{channelId}/wechat/callback`，GET 用于安全模式 URL 验证，POST 用于加密消息回调。

---

## 22. Channel Config / Secret 模型

当前 Generic Webhook 已经坚持 `credentialRef`，这个方向保留并扩展。

禁止：

- appSecret/token 写入 DB 普通 JSON；
- 前端回显真实 secret；
- 日志打印 token；
- test connection 返回 secret；
- Provider adapter 把 credential 放进 metadata。

推荐：

```text
ChannelConfiguration
  publicConfig
  credentialRefs
```

例如 Feishu：

```text
publicConfig:
  appId
  connectionMode
  requireMention

credentialRefs:
  appSecretRef
  verificationTokenRef
  encryptKeyRef
```

Secret Resolver 仍由 Trigger/Infrastructure 解析，Application 不接触原始 Secret。

---

## 23. Channel Frontend 重做

当前 `channel-management.tsx` 固定 `GENERIC_WEBHOOK`，并已经成长成同时处理：

- channel CRUD；
- test send；
- message history；
- recovery；
- identity mapping；
- outbox；
- workflow binding。

必须拆 feature。

目标：

```text
features/channels/
  api/
  model/
  providers/
    generic-webhook/
    feishu/
    wecom/
    dingtalk/
    slack/
    telegram/
    discord/
    qq/
    wechat/
  components/
  pages/
```

### 23.1 Channel 首页

使用 Provider Card Grid：

```text
Channels

Feishu       Connected   Healthy
WeCom        Connected   Healthy
DingTalk     Not set up
Slack        Not set up
Telegram     Not set up
Discord      Not set up
QQ           Not set up
WeChat       Not set up
Webhook      2 active
```

每张 Card 展示：

- Provider icon/name；
- connection status；
- health；
- bound project；
- inbound mode；
- last message；
- action required。

### 23.2 Setup Wizard

统一三步：

```text
1. Connect
2. Route & Access
3. Test
```

#### Connect

Provider-specific fields。

#### Route & Access

- Project；
- inbound NONE / REACT / WORKFLOW；
- Workflow version policy；
- unknown sender policy；
- group mention policy；
- identity/pairing policy。

#### Test

- credentials preflight；
- provider connection；
- inbound callback/long connection；
- outbound test；
- optional interactive callback test。

完成后显示 readiness，而不是“保存成功”就算接通。

---

## 24. Channel Health / Observability

每个 Channel 应有 typed health：

```text
CONFIGURED
CONNECTING
CONNECTED
DEGRADED
DISCONNECTED
AUTH_FAILED
RATE_LIMITED
DISABLED
```

Health Detail：

- last connected at；
- last inbound at；
- last outbound at；
- last provider error；
- consecutive failures；
- outbox backlog；
- inbound queue backlog；
- callback/WS status；
- token/auth expiration（若可感知）。

不要把这些塞进 Tool Routing Observability。

---

# Part D：实施顺序

## 25. Phase 0 — 前端基础与 Channel Core（先做）

目标：先把未来两条大改造的地基做好。

### 产品/前端

- 建立 route metadata；
- Sidebar 从 metadata 派生；
- 建立 `features/*` 目录；
- 拆 API client 基础层；
- 新增 Work / Automations / Projects / Platform 一级 IA；
- 保留旧 URL redirect；
- 建立 authority semantic tokens。

### Channel

- 把 `OpsChannelAdapter` 演进成 typed Provider SPI；
- 建立 `ChannelCapabilitySet`；
- 建立 typed inbound/outbound/action/health 模型；
- Generic Webhook 迁到新 SPI，功能不回退；
- 保留已有 Outbox / Recovery / Identity / ReAct / Workflow 主链；
- 默认 unknown sender policy 改为 DENY/PAIRING；
- 建立 Provider Contract Test Kit。

### Phase 0 验收

- Generic Webhook 现有行为全部回归通过；
- route/sidebar 单一配置源；
- 旧 route 可正常 redirect；
- Application/Domain 无 provider-specific JSON / dynamic Map；
- unknown sender 默认不能直接访问 Project ReAct。

---

## 26. Phase 1 — Work Workspace + Feishu / WeCom

### 产品

- Incident Workspace；
- Diagnosis 嵌入 Incident；
- Evidence Explorer；
- Change tab；
- Timeline；
- Approval Cockpit；
- Dashboard 改成 Attention & Action。

### Channel

- Feishu native adapter；
- WeCom native adapter；
- long connection lifecycle；
- rich cards；
- progress update；
- ChangePackage Approval card；
- Workflow Approval card；
- identity / pairing；
- health / preflight；
- provider-specific setup wizard。

### Phase 1 核心 E2E

```text
Feishu user
 -> inbound message
 -> identity resolved
 -> Project Default ReAct
 -> Incident/Run
 -> diagnosis result
 -> ChangePackage
 -> approval card
 -> user approve
 -> authoritative Approval
 -> Landing
 -> Verification
 -> same Feishu thread receives VERIFIED
```

WeCom 同样完整跑通。

---

## 27. Phase 2 — DingTalk / Slack + Project / Agent 前端收敛

### Channel

- DingTalk；
- Slack；
- rich interactive approval；
- progress/update；
- attachment。

### 前端

- Project Workspace Advanced 拆 feature tabs；
- Agent Builder Basic / Advanced；
- `agent-config.tsx` 拆分；
- `project-workspace.tsx` 拆分；
- Change Center 与 Incident Change tab 统一对象模型。

### Phase 2 验收状态（2026-08-17）

**完成。** 本阶段功能、前端结构和运行时装配已收口：

- DingTalk / Slack / Feishu / WeCom 共享 Channel provider contract，审批、进度更新、主动推送与附件能力按 capability 暴露；
- Project Workspace Advanced 已拆为 Overview / Access / Knowledge & Skills / Code & Runtime / Data & Tools，并完成 feature 组件物理拆分；
- Agent Builder 默认 Basic，Advanced 承载版本、条件边、高级 JSON、运行默认值和节点内 MCP 等低频配置；
- Change Center 与 Incident Change 已统一到 canonical `OpsChangePackage` 派生的 `ChangePackageSummary` 展示模型；
- Channel provider 在 outbound / protocol catalog 侧改为运行时按需解析，避免启动阶段反向实例化 inbound driver；Chat 与 Workflow approval resume 共享唯一生产 `OpsWorkSessionRunApplicationFacade`。

验收结果：

- Web `npm run verify:full`：58/58 Vitest、9/9 Playwright Chrome、typecheck、build、release hygiene 全通过；
- Channel / Approval / Workflow 组合回归：123/123 通过；
- DDD / Architecture 门禁：792/792 通过；
- Backend `mvn -q -pl orbisops-app -am test`：完整 reactor 测试通过（exit 0），包含真实 Spring ApplicationContext、Testcontainers MySQL 与 MCP stdio 链路；
- Server release hygiene：通过。

---

## 28. Phase 3 — Telegram / Discord / QQ

### Channel 子阶段状态：已完成

- Telegram：Bot API long polling、发送/原消息更新、Inline Keyboard action；
- Discord：Gateway v10、MESSAGE_CONTENT intent gate、发送/原消息更新、Button component action；
- QQ：当前原生 Gateway、C2C / 群 @Bot、发送、Inline Keyboard action；
- capability fallback：前端只依据后端 capability descriptor 展示能力，不按 Provider 名称猜测；
- provider contract：Feishu / WeCom / DingTalk / Slack / Telegram / Discord / QQ 复用统一长连接 contract；
- setup UX：三类 Provider 的 credential ref、连接模式、必要权限/字段和能力降级已进入 Channel Setup；
- 生命周期：Provider transport 统一由 Channel supervisor 在 ContextClosedEvent 释放，integration adapter 不直接依赖容器生命周期注解；
- Test infrastructure：移除历史 Surefire `3.0.0-M7` 固定版本，跟随 Spring Boot 3.4.3 管理的 Surefire `3.5.2`，解决 full-reactor fork 下偶发 class loading 失败。

本阶段验收：

- Phase 3 Channel + architecture 定向回归：通过；
- Backend `mvn -q -pl orbisops-app -am test`：exit 0，包含真实 Spring ApplicationContext、Testcontainers MySQL 与 MCP stdio；
- Frontend `npm run verify`：release hygiene、typecheck、61/61 Vitest、production build 全部通过；
- Chrome Playwright：10/10 全量 E2E 通过，其中 Phase 3 Channel Setup/capability 场景通过；
- Server release hygiene：通过。

### Knowledge 子阶段状态：已完成

- `rag-order-management.tsx` 已收敛为兼容路由壳，真实实现迁入 `features/knowledge`；
- Knowledge 已按 `pages / api / model / components` 拆分，页面不再直接依赖 RAG HTTP service；
- global bases / chunks / stats / usage / ingestion jobs / eval / feedback / gaps 已迁入 TanStack Query，mutation 使用精确 query invalidation；
- 入库任务轮询由 Query 根据 `PENDING/RUNNING` 状态按需启停，后台窗口不轮询，删除页面级 `useEffect + setInterval`；
- search filter 与 `selectedKnowledgeId` 操作上下文分离，模糊搜索不会误触发详情、策略、删除或评测上下文；
- 检索策略归一化/校验、Knowledge identity/status 兼容、上传约束、Overview 聚合已下沉纯 model 并覆盖单测；
- 新增 feature boundary contract，禁止页面重新引入 direct service / page-owned server state；
- Chrome E2E 验证未选择或仅模糊搜索知识库时不会请求 chunks/usage，明确选择后才加载详情。

Knowledge 子阶段验收：

- Web `npm run verify`：release hygiene、typecheck、68/68 Vitest、production build 全部通过；
- Chrome Playwright：11/11 全量 E2E 通过，新增 Knowledge search/context 隔离验收；
- feature boundary contract：2/2 通过，页面无 direct service / `useEffect` / 手工 polling server state。

### Phase 3 前端产品化 / Server State 收敛状态：已完成

在 Knowledge 子阶段之后继续完成了 Platform IA 与主要页面 server-state 收敛。本轮原则不是追求“零 `useEffect`”，而是消除页面自维护的 GET 数据副本、手工 reload/polling 与重复请求；URL 深链、流式 Run、编辑草稿同步、编辑器 hydration 等真正的 UI / 生命周期协调继续保留。

已完成：

- Platform Settings 从 12 个扁平入口重组为 Integrations / Intelligence / Execution & Governance / Advanced，并保留旧 canonical deep links；
- Models、Execution Targets、Analysis Tasks、Governance / Audit Policy 全部迁入 TanStack Query / Mutation；
- Channel Management 的列表、详情、消息、身份、Outbox 与写操作迁入 Query / Mutation；修复 100 条消息历史与 200 条身份候选复用同一 Query Key 导致的缓存污染；
- Tools / MCP、Memory、Tool Routing、Skill Evolver、Skills、Dashboard / My Audit、Workflow Catalog、Inspection Schedules、Alert Triggers 全部完成 server-state 边界收敛；
- Incident Center 的列表、详情、成员与协作命令收敛到 Query / command mutation，并复用全局 Project Scope，消除管理员重复 Project Snapshot；
- Change Center 的 ChangePackage 列表、详情、Events、Landing Operation Runs 与 validate / submit review / approve / reject / land / cleanup 收敛到 Query / Mutation；保留 capability gate、version/hash 与 Landing 安全校验；
- Project Workspace 复用全局 Project Scope 的 `projects + templates` snapshot，项目 Skills / Knowledge / Members / Accounts 与 Code / Runtime 数据进入项目级 Query，移除页面三组 `useEffect(load)` 与第二份项目快照；
- Agent Builder 的 MCP / Knowledge / Model / Skill catalog、Project Capability 与发布引用影响进入 Query；Workflow 读取后的编辑器初始化继续作为 UI hydration 生命周期处理，不把本地草稿误当成 server state；
- 最终残余检查确认：Alert 登录校验、Analysis / Change 深链、Chat 流式会话、Skills 草稿/弹窗同步、Agent Builder 草稿与 editor hydration 等剩余 effect 均不是页面级 GET server-state。

最终前端验收基线（2026-08-17）：

- `npm run verify`：release hygiene、typecheck、**105/105 Vitest**、production build 全部通过；
- `npm run test:e2e`：**30/30 Chrome Playwright** 全量通过；
- Project Workspace 浏览器验收确认：admin snapshot 首次仅请求 1 次，显式刷新才重新请求；
- Channel / Knowledge / Models / Execution Targets / Analysis Tasks / Governance / Tools / Memory / Tool Routing / Skills / Automations / Incidents / Changes 等 Query boundary 均有 contract 或 E2E 防回归；
- 最终再次执行 Backend `mvn -q -pl orbisops-app -am test`：exit 0，包含真实 Spring ApplicationContext、Testcontainers MySQL 与 MCP stdio 链路；本轮后续未修改后端实现。

Phase 3 至此收口，后续进入 Phase 4。

---

## 29. Phase 4 — WeChat + Advanced Governance + 产品抛光

### 29.1 WeChat Provider：已完成

- Native Provider：WeChat Official Account Secure Webhook；
- 公众号安全模式 SHA-1 signature + AES-CBC / PKCS#7 解密；
- AppID binding 校验，拒绝跨 App callback；
- text DM 进入既有 `ReceiveChannelMessageUseCase.receiveProvider` durable / identity / access / execution pipeline；
- 非文本、DISABLED、Outbound-only Channel 安全 ACK，不误触发 Runtime；
- 客服文本 API access token provider-local cache，不持久化、不日志输出；
- Setup Wizard 已支持 AppID + 三类 env ref，并在保存后的 Test 步骤直接展示 Callback URL；
- capability truthfulness：无 Group / Approval button / Attachment / Message Update / unrestricted Proactive Push；
- 未配置真实凭据或尚未产生真实消息时 readiness 保持 Blocked / Action Required，不制造 PASS。

Phase 4 WeChat 验收（2026-08-17）：

- WeChat Provider / secure codec / API client / webhook service 定向后端测试全绿；
- `ArchitectureTests` 全绿；
- Backend `mvn -q -pl orbisops-app -am test`：**exit 0**，真实 Spring Context、Testcontainers MySQL、MCP stdio 均通过；
- `npm run verify`：release hygiene、typecheck、**106/106 Vitest**、production build 全绿；
- `npm run test:e2e`：**31/31 Chrome Playwright** 全绿，包含 WeChat Setup Wizard / env ref / Callback URL / readiness 浏览器验收。

继续 Phase 4：

- Channel compatibility matrix；
- Global Search / Command Palette；
- Advanced Governance；
- Accessibility；
- Responsive；
- GitHub Hero screenshot / demo GIF；
- onboarding / activation telemetry。

---

# Part E：测试与验收

## 30. Channel Contract Test Kit

每个 Provider 必须复用同一套 contract：

### Configuration

- required fields；
- invalid secret ref；
- disabled channel；
- preflight success/failure；
- health transitions。

### Inbound

- valid message；
- duplicate message；
- invalid signature；
- unknown sender；
- mapped sender；
- group mention；
- oversized payload；
- attachment metadata；
- reconnect/replay。

### Outbound

- success；
- transient failure；
- rate limit；
- permanent auth failure；
- idempotent retry；
- no capability fallback；
- outbox recovery。

### Interactive Action

- approve；
- reject；
- revision request；
- duplicate click；
- stale card；
- wrong user；
- wrong project；
- removed project membership；
- already resolved approval；
- changed ChangePackage version。

---

## 31. Security Acceptance

必须证明：

1. Secret 不进数据库普通字段、不进日志、不进审计 payload；
2. Provider callback/connection 有可信验证；
3. unknown sender 默认不能进入项目调查；
4. 未绑定身份绝不能审批；
5. Channel Approval 不绕过平台 RBAC；
6. Channel Approval 不绕过 ChangePackage version/state；
7. Channel 不能直接调用生产 mutation capability；
8. Landing 仍然只能由 trusted Landing runtime 获取 `PROD_FULL`；
9. duplicate/replay 不会重复执行；
10. outbound URL / webhook 继续遵守 SSRF policy；
11. payload/body/file 有大小限制；
12. 媒体下载有 content-type/size/path traversal 防护；
13. 不可信文件不会直接进入可执行 workspace；
14. Provider 异常不能拖垮 Channel worker 全局线程。

---

## 32. 前端架构验收

### 必须做到

- Route / Sidebar / RBAC navigation 单一 metadata 源；
- 新页面不直接依赖 God Service；
- 新 server state 使用 TanStack Query；
- Incident / Change / Channel feature 有独立 API/model/component；
- page component 不承载复杂 domain policy；
- Approval button permission/state matrix 有纯 TS 测试；
- workflow builder validation 有纯 TS 测试；
- Channel provider config schema 有测试；
- responsive 至少覆盖 390px / tablet / desktop；
- keyboard / focus / modal 可访问性基础通过。

### 不设机械 LOC KPI

不以“每个文件必须小于 500 行”作为形式主义门禁，但以下是 code smell：

- 一个 Page 同时负责 CRUD + validation + networking + modal + domain mapping + table + polling；
- 一个 Service 横跨多个 bounded feature；
- 一个文件定义多个互不相干的业务模型；
- Router 与 Sidebar 重复写权限和标签。

---

## 33. Product Acceptance

### First Value

新管理员应能沿单一路径完成：

```text
Create Project
 -> Connect one evidence source
 -> Confirm Project Ready
 -> Run first diagnosis
 -> See grounded evidence
```

无需先理解所有 MCP / Skill / Memory / RAG 内部概念。

### Incident Resolution

必须可以从一个 Incident Workspace 完成：

```text
Investigate
 -> Review Evidence
 -> Prepare Change
 -> Approve
 -> Landing
 -> Verify
 -> Resolve
```

不要求用户在多个模块中自己寻找对应对象。

### Channel First Value

每个 Provider 的 Setup Wizard 必须明确给出：

```text
Credentials: OK
Connection: OK
Inbound: OK
Outbound: OK
Identity/Access: OK
Ready
```

不允许“保存配置成功”冒充“Channel 可用”。

---

# Part F：明确不做的事情

## 34. 不重新发明 Agent Runtime

Channel 不增加另一套：

- ReAct runtime；
- Workflow runtime；
- tool permission；
- memory runtime；
- production mutation runtime。

所有 Channel 最终仍进入现有 Application / Runtime 主链。

## 35. 不让 Provider 污染 Domain

禁止出现：

```text
FeishuApprovalDomainService
WeComIncidentDomainService
SlackChangePackageService
```

Provider 只在 Adapter / ACL 层。

## 36. 不把 Channel Action 当审批事实

Card click / button click 是 Command，不是 Approval Record。

## 37. 不强求所有 Channel 功能完全对称

以 Capability + Fallback 处理平台差异，不为“表格里全打勾”写脆弱兼容代码。

## 38. 不为前端“看起来 DDD”制造过度抽象

前端 feature-first 的目标是：

- 边界清晰；
- 页面变薄；
- server state 一致；
- domain interaction 可测试。

不是把每个 React component 拆成十层 facade/repository/usecase。

---

# Part G：最终目标形态

## 39. 产品

用户最终看到的是：

```text
Home
Work
Automations
Projects
Platform
```

而不是几十个 Agent 内部技术对象。

## 40. Work

一个生产问题从发生到恢复都围绕同一个 Incident：

```text
Alert / Chat / Channel
      -> Incident
      -> Diagnosis
      -> Evidence
      -> ChangePackage
      -> Approval
      -> Landing
      -> Verification
      -> Resolved
```

## 41. Channel

同一个 OrbisOps Work 可以从任何入口触发和持续：

```text
Web
Feishu
WeCom
DingTalk
Slack
Telegram
Discord
QQ
WeChat
Generic Webhook
```

它们共享：

- Project；
- Identity；
- Incident；
- Conversation / Run；
- Agent / Workflow；
- Evidence；
- ChangePackage；
- Approval；
- Landing；
- Audit。

不同的只是交互协议和表现形式。

## 42. 技术结构

```text
                    OrbisOps Application
                           |
          +----------------+----------------+
          |                |                |
        Work          Automation        Platform
          |                |                |
          +----------------+----------------+
                           |
                    Channel Semantics
                           |
              Channel Provider SPI / ACL
                           |
   +-------+--------+-------+------+-------+-------+
   |       |        |       |      |       |       |
 Feishu  WeCom  DingTalk  Slack Telegram Discord  ...
```

Channel Provider 不拥有业务，业务也不知道 Provider 协议。

---

## 43. 推荐实施顺序总结

严格按以下顺序推进：

1. **前端 Route Metadata + Feature Skeleton + Channel typed SPI**；
2. **Incident Workspace + Approval Cockpit**；
3. **Feishu + WeCom + Channel Approval 全闭环**；
4. **DingTalk + Slack**；
5. **Project / Agent Builder 前端重构**；
6. **Telegram + Discord + QQ**；
7. **Knowledge / Platform Settings / TanStack Query 全面收敛**；
8. **WeChat**；
9. **Global Search / Advanced Governance / Accessibility / 产品视觉抛光**；
10. **完整多 Channel E2E、安全验收、README Demo 素材后发布**。

每个 Phase 都必须保持：

- 后端 DDD 门禁全绿；
- 既有 ReAct / Workflow 语义不回退；
- ChangePackage / Approval / Landing 权威链不旁路；
- Generic Webhook 不回退；
- 前端 typecheck/test/build 全绿；
- Channel Provider contract 全绿；
- 真实 Provider smoke 只能在有真实凭据时标记 PASS，没有凭据必须明确 SKIP/BLOCKED_EXTERNAL，不能用 mock 冒充真实平台验收。

---

## 44. 参考资料

本方案参考了 reference channel implementation 2026-08-15 前公开的项目 README、Architecture、Channel/API、Workflow、Feishu/WeCom 与 v1.4/v1.7 release notes，重点借鉴：

- 多 Channel Provider SPI；
- 8 个 IM 平台覆盖；
- Channel health / preflight；
- Feishu first-class rich card；
- Feishu / WeCom channel approval resolve -> resume；
- long-task progress；
- Provider-specific transport + unified application semantics。

OrbisOps 不复制 reference channel implementation 的 Agent/Approval 权限模型；所有生产变更仍以 OrbisOps 自己的 `ChangePackage -> Approval -> Trusted Landing -> Verification` 为唯一权威路径。
