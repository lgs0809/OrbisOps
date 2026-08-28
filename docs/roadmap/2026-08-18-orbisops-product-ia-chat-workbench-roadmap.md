# OrbisOps 产品信息架构、Chat / Workbench 与平台设置重构方案

> 日期：2026-08-18
> 状态：Approved Product Direction / Implementation Roadmap
> 适用仓库：当前 OrbisOps 仓库根目录
> 目标：在不推翻现有 ReAct / Workflow / ChangePackage / Landing / Verification 运行时与安全边界的前提下，重构 OrbisOps 的产品信息架构、核心页面关系和首次部署体验，使其从“按技术子系统堆页面”收敛为“按用户任务组织产品”。

---

## 1. 本轮为什么要重构

当前 OrbisOps 的核心运行能力已经比较完整，但前端信息架构仍明显受到历史代码模块和后端领域对象影响。当前导航大致为：

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
  Overview
  Integrations
    Channels
  Intelligence
    Models
    Knowledge
    Skills
  Execution & Governance
    Tools / MCP
    Execution Targets
    Governance
  Advanced
    Model Catalog
    Memory
    Skill Evolver
    Tool Routing
    Analysis Tasks
```

这带来几个产品问题：

1. 用户需要先理解 `Incident / Diagnosis / Evidence / Change / Approval / Verification` 等领域术语，才能知道去哪处理问题。
2. Workflow 被错误地归类到 Automations 下，暗示 Workflow 只服务自动化；但实际上 Chat 也可以显式选择 Workflow，Schedule / Alert 也都只是 Workflow 的调用方之一。
3. Chat 被改名并隐藏在 Diagnosis 心智下，削弱了最自然的人工交互入口。
4. Incidents 与 Chat / Diagnosis 的边界对用户不自然，容易形成两套重复的问题调查界面。
5. Platform 导航暴露大量实现概念，如 Memory、Skill Evolver、Tool Routing、Model Catalog，普通用户无法从任务心智理解这些入口。
6. Project 页面承载了平台账号创建等不属于 Project 的职责。
7. 首次管理员依赖部署环境变量 Bootstrap，不符合一个成熟自部署产品的默认首次使用流程。
8. Header 仍出现 JWT 等实现细节，整体页面更像内部控制台而不是完整产品。

本轮不是重做底层架构，而是让已有能力以正确的用户心智暴露出来。

---

## 2. OrbisOps 的最终产品定位

OrbisOps 不是 Agent Library，也不是 Workflow Automation SaaS。

它的核心价值是：

> 让运维团队通过受治理的 Agentic 能力，安全地调查、执行、审批、落地和验证真实运维工作。

用户不应该首先面对：

```text
Agent Type
Tool Registry
MCP Runtime
Incident Domain
Skill Evolver
Execution Binding
```

用户首先应该面对的是：

```text
我要和 Agent 交流
我要看正在发生的运维任务
我要定义一个可复用流程
我要定义何时自动执行
我要配置一个业务项目
我要审批 / 跟踪生产变更
我要配置平台
```

因此，一级导航按“用户任务”组织，而不是按“领域实体”或“技术模块”组织。

---

## 3. 最终一级导航

目标一级导航固定为：

正式 UI 的一级导航显示名固定为：

```text
首页
对话
工作台
工作流
自动化
项目
变更
设置
```

内部 route id / path 可以继续使用 `home`、`chat`、`workbench`、`workflows`、`automations`、`projects`、`changes`、`settings`，但不能把这些英文内部标识直接当作用户可见导航文案。

正式 UI 用户可见文案统一中文；ReAct、Workflow、MCP、Skill、ChangePackage、Landing、Verification、Runtime、Project、Agent 等已形成稳定心智的技术术语可按语义保留英文。

### 3.1 不再作为一级导航的概念

以下概念可以继续存在于领域模型、Run 详情或 Change 生命周期中，但不应再占一级导航：

```text
Incident
Diagnosis
Evidence
Approval
Landing
Verification
```

其中：

- `Diagnosis` 是 Chat / Run 中发生的调查行为，不是独立用户入口。
- `Evidence` 是 Run / Task / Change 的可信依据，不是独立工作区。
- `Approval / Landing / Verification` 是 ChangePackage 生命周期阶段。
- `Incident` 后端可继续保留用于告警、问题生命周期、审计和兼容现有逻辑，但前端不强迫用户先理解 Incident 才能工作。

---

## 4. 最重要的产品边界

### 4.1 Chat 与 Workbench 必须区分

这两个页面不能合并。

#### Chat

定义：

> 人主动与 OrbisOps Agent 交流、问答、排障、查询、手动调用 Workflow 的正常交互页面。

典型场景：

```text
“支付接口现在健康吗？”
“Redis 当前连接数多少？”
“帮我排查今天的 P1 延迟问题。”
“运行一次数据库专项巡检 Workflow。”
```

Chat 体验应接近正常 AI Chat，而不是工单页面。

Chat 不应默认要求：

```text
Severity
Owner
Incident ID
Change State
```

普通轻量 Chat 也不必全部进入 Workbench。

#### Workbench

定义：

> 结构化管理正式运维任务、Run、长执行、自动化执行、失败 / 阻塞 / 等待人工处理执行的 Operations Console。

Workbench 不是聊天页。

Workbench 重点回答：

```text
现在有哪些任务正在运行？
哪些执行失败了？
哪些需要人工关注？
哪些自动化刚跑完？
哪些长任务还没结束？
```

Workbench 中可以有 `Continue in Chat`，但不要在 Workbench 内重新实现一套完整 Chat UI。

### 4.2 Chat 与 Workbench 的共享对象是 Run

关系：

```text
Chat
   \
    → Run → Workbench
   /
Automation
```

- Chat 发起轻量查询：可以只有 Conversation，不产生需要 Workbench 跟踪的正式任务。
- Chat 发起长时间 / durable / Workflow / 需要人工接管的正式执行：产生正式 Run，Workbench 能跟踪同一 Run。
- Automation 触发执行：产生正式 Run，默认进入 Workbench。
- Workbench 点击 `Continue in Chat`：回到原 Conversation，或创建一个携带 Run / Evidence / Findings 上下文的新 Conversation。

不要复制两套 Run 数据。

---

## 5. Workflow 与 Automation 必须彻底解耦

这是本轮不可回退的产品原则。

### 5.1 Workflow = 怎么执行

Workflow 是：

> 人类为成熟运维场景定义的、可版本化、可发布、可复用的宏观执行流程。

例如：

```text
Database Health Check
P1 Incident Investigation
Release Preflight
Post-release Verification
```

Workflow 可以被以下入口调用：

```text
Chat
Channel
Schedule
Alert
Manual Run
Inspection-like automation
API
```

因此 Workflow 必须独立一级入口，不能放在 Automations 下面。

### 5.2 Automation = 什么时候触发

Automation 是：

> 定义何时 / 在什么事件下自动启动一次执行。

触发方式包括：

```text
Schedule
Alert
Event
Webhook
```

执行方式可以是：

```text
Project Default ReAct
Published Workflow
```

统一模型：

```text
Trigger
  ↓
Project
  ↓
Execution Binding
  ├── DEFAULT_REACT
  └── WORKFLOW
  ↓
Run
  ↓
Workbench
```

不能把 Automation 后端写死成 `Schedule → workflowId` 或 `Alert → workflowId`。

---

## 6. Workflow Builder 的最终边界

### 6.1 Workflow Canvas 节点

宏观流程节点保持简单：

```text
Start
Agent
Router
Sub Workflow
End
```

不要新增：

```text
MCP Node
Tool Node
Skill Node
Knowledge Node
Review Node
```

### 6.2 Agent Node execution mode

固定只有：

```text
DIRECT
LLM
REACT
```

没有 `REVIEW`。

如果需要 review / judge / quality check：

```text
LLM
+ reviewer prompt / role
+ structured output contract
```

即可，不创造第四种 execution mode。

### 6.3 Agent Node capability

能力作为 Agent Node 属性：

```text
Prompt
Execution Mode
Model
Tools / MCP
Skills
Knowledge
Memory
Input Mapping
Output Contract
Timeout
Retry
```

Workflow 表达宏观控制流；Tool / MCP / Skill / Knowledge 属于节点能力。

### 6.4 Builder UI

Workflow Builder 使用沉浸式布局，进入后可隐藏普通 Sidebar：

```text
← Workflows     Database Health Check     Draft v5     Save   Publish

Node Palette            Canvas                       Inspector
Start                    ...                          Mode
Agent                                                 Prompt
Router                                                Model
Sub Workflow                                          Tools / MCP
End                                                   Skills
                                                      Knowledge
                                                      Timeout
                                                      Retry
```

---

## 7. Chat 页面方案

### 7.1 Chat 的职责

Chat 是最自然的人工入口。

布局：

```text
┌──────────────┬────────────────────────────────────┐
│ Conversations│ Project: Payment Service ▼         │
│              │ Execution: Default ReAct ▼         │
│ Today        │────────────────────────────────────│
│ Yesterday    │ Conversation                       │
│ ...          │                                    │
│              │                                    │
│              │────────────────────────────────────│
│              │ [ Message... ]                     │
└──────────────┴────────────────────────────────────┘
```

### 7.2 Project Context

用户只选择 Project，不手工选择一堆底层 capability。

Project 自动提供授权范围内的：

```text
Resources
Tools / MCP
Knowledge
Skills
Memory
Code / API context
Runtime policy
```

### 7.3 Execution Selector

Chat 顶部执行方式：

```text
Default ReAct

Workflows
  Database Investigation
  P1 Incident Triage
  Release Verification
```

即：

```text
Chat
├── Project Default ReAct
└── Published Workflow
```

不要出现 Agent Library 或 ReAct Agent / Workflow Agent 作为主要产品分类。

### 7.4 执行过程展示

默认只展示用户有价值的信息：

```text
Checking request latency and dependency health…
Queried Prometheus
Found: P95 increased from 320ms to 2.7s
```

详细技术执行通过折叠区或详情抽屉查看：

```text
Run Steps
Tool Calls
Evidence
Logs
```

不要把内部 reasoning / Runtime 噪声直接铺满正常 Chat。

---

## 8. Workbench 页面方案

### 8.1 Workbench 列表

目标过滤：

```text
All
Needs Attention
Running
Completed
Failed
```

主表字段：

```text
Name
Source
Project
Execution
Status
Owner / Assignee (if applicable)
Updated At
```

Source 可包括：

```text
Chat
Schedule
Alert
Channel
Manual
API
```

### 8.2 什么进入 Workbench

应该进入：

```text
Automation Run
Workflow Run
Durable / long-running Run
Failed / blocked Run
Waiting for human action
Chat 发起的正式任务
Change-related execution
```

不要求进入：

```text
简单问答
一次短查询
普通知识解释
无持续管理价值的临时 Chat
```

### 8.3 Task / Run Detail

详情重点：

```text
Summary
Execution Timeline
Evidence
Findings / Result
Related Change
Source / Trigger
Project Context
```

优先用“主时间线 + 右侧摘要”而不是制造很多二级 Tab。

提供：

```text
Continue in Chat
```

将当前 Run Context 带入 Chat。

### 8.4 Workbench 后端模型

Workbench 是 application/query projection，不新造 `WorkbenchDomainAggregate`。

建议后端提供统一 Query Projection，例如：

```text
GET /api/v1/workbench/runs
```

支持：

```text
status
source
projectId
executionType
owner
needsAttention
page
```

返回统一视图：

```text
id
title
project
source
executionType
status
startedAt
updatedAt
needsAttention
relatedChangeId
```

底层数据可继续来自现有 Incident / Runtime / Workflow / Automation / Change 等模型。

---

## 9. Projects 页面方案

Project 是业务、能力和运行安全边界，不是普通文件夹。

Project 详情收敛为 5 个区域：

```text
Overview
Resources
Capabilities
Members
Runtime
```

### 9.1 Overview

包含：

```text
Name
Description
Environment
Health / readiness
Default execution
Recent Runs
Recent Changes
```

### 9.2 Resources

展示该业务系统的真实资源，例如：

```text
MySQL
Redis
Kafka
Prometheus
Kubernetes
Code Repository
OpenAPI
```

### 9.3 Capabilities

将目前散落的配置收成一个能力页面：

```text
Default ReAct
Tools / MCP
Knowledge
Skills
Available Workflows
```

不要为每类 capability 再制造一个 Project 子 Tab。

### 9.4 Members

只允许：

```text
Add existing platform user
Remove member
Assign project role
```

禁止在 Project 中创建平台账号。

### 9.5 Runtime

管理：

```text
DEV / TEST / PROD runtime boundaries
Read-only policy
Execution target
Landing policy
Production authority
```

保持现有安全模型，不因 UI 简化而弱化权限。

---

## 10. Changes 页面方案

Changes 保留一级入口，因为审批人 / 生产变更负责人需要跨 Project 的统一治理 Inbox。

### 10.1 列表状态

```text
Pending Approval
Approved
Landing
Verification
Completed
Failed
```

### 10.2 Change Detail

统一围绕 ChangePackage 生命周期组织：

```text
Summary
Problem & Evidence
Change Plan
Artifact / Diff
Affected Targets
Risk
Rollback Plan
Verification Plan
Approval
Landing
Verification
Timeline
```

不要把 Approval / Landing / Verification 再做一级导航。

### 10.3 固定运行边界

普通 Chat / Workflow / Investigation Runtime：

```text
PROD read-only
```

需要生产修改时：

```text
Run / Chat
  ↓
ChangePackage
  ↓
Approval
  ↓
platform-landing-react
  ↓
PROD_FULL
  ↓
Verification
```

ChangePackage 是任务书 / 审批对象，不是第二套逐 Tool / resource / exact args ACL。

如果原方案不成立：

```text
NEEDS_REPLAN
→ REVISING
→ new version
→ re-approval
```

如果方案仍成立但本次执行失败：

```text
LANDING_FAILED
```

不自动等同于重新规划。

---

## 11. Automations 页面方案

Automations 只管理自动触发规则。

页面内部可用过滤或分类：

```text
All
Schedules
Event Triggers
```

不需要在 Sidebar 下再次展开很多项。

### 11.1 Create Automation

建议流程：

```text
1. Trigger
2. Project
3. Execution
4. Policy
```

Trigger：

```text
Schedule
Alert
Event
Webhook
```

Execution：

```text
Project Default ReAct
Published Workflow
```

Policy：

```text
Concurrency
Timeout
Retry
Deduplication
```

### 11.2 Automation Detail

只展示定义和运行摘要：

```text
Configuration
Recent Runs
Success Rate
Recent Failures
```

具体 Run 点击后进入 Workbench。

---

## 12. Settings 页面方案

Settings 作为一个一级入口，内部使用 Settings Home 分类卡片，不要把所有设置项永久展开在主 Sidebar。

目标分类：

```text
Users & Access
Models
Channels
Tools & MCP
Knowledge
Skills
Execution Targets
Governance & Audit
Advanced
```

### 12.1 Users & Access

平台管理员创建用户：

```text
Settings
→ Users & Access
→ Create User
```

之后：

```text
Projects
→ Members
→ Add Existing User
```

平台用户生命周期和 Project Membership 分离。

### 12.2 Models

管理：

```text
Chat models
Embedding models
Rerank models
```

用户自己配置 Provider / API，不依赖 OrbisOps 预置私有模型服务。

### 12.3 Channels

Channel 明确归位：

```text
Settings
→ Channels
```

当前 `Platform → Integrations → Channels` 改为 Settings 入口。

支持的 Channel 可包括：

```text
Feishu
WeCom
Slack
DingTalk
```

Channel 配置内容包括：

```text
Connection / Credential
Callback / Signing
Identity Mapping
Project Routing
Allowed Actions
```

Channel 是命令入口 / 接入配置，不是主业务工作区。

Channel 触发正式执行后：

```text
Channel
  ↓
Identity / Project Routing
  ↓
ReAct / Workflow
  ↓
Run
  ↓
Workbench
```

审批回调仍必须经过：

```text
signature
identity
project membership
RBAC
approval policy
ChangePackage version / state
ApprovalApplicationService
```

不能因为 Channel 上有 Approve 按钮就直接执行 Landing。

### 12.4 Advanced

以下内部能力统一收进 Advanced，并默认弱化：

```text
Model Catalog
Memory
Skill Evolver
Tool Routing
Analysis Tasks
```

不要再和 Chat / Workbench / Changes 同权重暴露。

---

## 13. 首次启动与账号体系

### 13.1 默认首次启动体验

当前实现依赖：

```text
ORBISOPS_BOOTSTRAP_ADMIN_ENABLED
ORBISOPS_BOOTSTRAP_ADMIN_USERNAME
ORBISOPS_BOOTSTRAP_ADMIN_PASSWORD
```

作为高级无人值守 Bootstrap 可以保留，但不应是公开 README 的默认首次使用流程。

默认产品流程：

```text
Start OrbisOps
  ↓
Open browser
  ↓
No platform users exist
  ↓
First-time Setup
  ↓
Create first administrator
  ↓
Auto sign in
```

页面字段：

```text
Username
Password
Confirm Password
```

### 13.2 后端要求

建议新增：

```text
GET /api/v1/setup/status
POST /api/v1/setup/admin
```

必须满足：

1. 仅系统完全无用户时允许 setup。
2. 第一位用户固定 ADMIN。
3. 创建必须事务 / 原子化，防止并发创建多个首任 Admin。
4. Setup 成功后永久关闭创建首任 Admin 的公开能力。
5. 具备密码校验、限流、安全审计。
6. 不在仓库或镜像内嵌默认账号 / 默认密码。

### 13.3 Login

普通 Login 简化为：

```text
OrbisOps
Sign in
Username
Password
[ Sign in ]
```

首次未初始化时同一个 Shell 切换为：

```text
Set up OrbisOps
Create the first administrator account.
Username
Password
Confirm password
[ Create administrator ]
```

不要继续使用复杂营销式左右两栏登录页。

---

## 14. Header / Sidebar / 视觉信息层级

### 14.1 Sidebar

目标宽度约：

```text
240px expanded
64–72px collapsed
```

只呈现一级导航：

```text
Home
Chat
Workbench
Workflows
Automations
Projects
Changes
Settings
```

不把 Settings 内部几十个设置项展开到主导航。

### 14.2 Header

移除无用户价值的实现信息，例如：

```text
JWT protected
生产控制台
Agent 工作台
```

Header 只保留：

```text
Current Project context (where applicable)
Search / Command
Run / attention indicator (if useful)
User menu
```

### 14.3 视觉原则

- 页面减少 Card-in-Card。
- 配置页优先：Title + Primary Action + Filter + Table / Form。
- Dashboard / Home 才大量使用摘要卡片。
- 主内容最大宽度约 1440px。
- 圆角 8–12px，细边框，中性背景。
- Sidebar / Header / Table / Form 保持清晰密度，不做过度装饰。
- Workflow Builder 使用沉浸式画布。

---

## 15. Home 页面

Home 回答：

> 今天有什么需要我处理？

建议结构：

```text
Needs Attention
Running
Recent Activity
Quick Actions
```

Needs Attention 只显示真正可操作的问题：

```text
Failed Runs
Pending Change Approvals
Verification Failures
High-priority automation failures
```

不要显示：

```text
JWT status
MCP count
Model provider count
Agent count
```

Quick Actions：

```text
New Chat
Create Workflow
Create Automation
Create Project
```

---

## 16. 用户可见语言

正式 UI 用户可见文本统一中文：

```text
Navigation
Buttons
Form labels
Empty states
Toasts
Validation errors
API errors shown in UI
```

保留行业术语：

```text
ReAct
Workflow
MCP
Skill
ChangePackage
Landing
Verification
```

代码内部注释 / 日志不属于本轮必须全部翻译的范围，但用户直接可见的后端错误消息应同步中文化；后端枚举、协议字段和审计标识保持稳定，不为翻译改动契约。

---

## 17. 后端需要改什么，哪些不需要改

本轮原则：

> 前端和产品信息架构大改，后端只做正确产品行为所需的最小支撑，不推倒现有 DDD / Runtime / 数据模型。

### 17.1 保持不动的核心

```text
ReAct Runtime
Workflow Runtime
Durable Execution
MCP / Tool capability
Skill
Knowledge
Project
ChangePackage
Approval
Landing
Verification
Channel Adapter
Production permission boundary
```

### 17.2 必须 / 高概率需要新增或调整

#### A. First-time Setup API

必须新增，见第 13 节。

#### B. Workbench Query Projection

高概率需要增加统一 Run 查询，而不是让前端拼多个 API。

#### C. Conversation ↔ Run 明确关联

需要能表达：

```text
conversationId
runId
projectId
```

支持 Chat 发起正式 Run 后 Workbench 跟踪，也支持 Workbench `Continue in Chat`。

#### D. Automation ExecutionBinding

检查现有实现，统一保证 Automation 可执行：

```text
DEFAULT_REACT
WORKFLOW
```

不要只绑定 workflowId。

#### E. Platform User 与 Project Member 职责分离

若后端已独立，只改前端入口；若应用服务耦合，则拆应用层职责，不一定改数据库。

#### F. Changes list / filter projection

如果现有 ChangePackage API 不能方便支持跨 Project Pending Approval / Landing / Verification 查询，则补 Query API。

### 17.3 不应为了前端重构去做的事

- 不新造 `Workbench` Domain Aggregate。
- 不为了隐藏 Incident 页面就删 Incident 后端模型。
- 不重写 ChangePackage / Approval / Landing 状态机。
- 不因为页面合并就合并数据库表。
- 不把 Tools / MCP / Skill 改造成 Workflow Node。
- 不新增 REVIEW execution mode。

---

## 18. 当前代码已确认的重构点

实施时优先核对以下现状：

1. `web/src/app/navigation/route-metadata.ts`
   - 当前 `NavigationSection = home | work | automations | projects | platform`。
   - `Incidents / Diagnosis / Changes` 在 Work。
   - `Workflows / Inspections / Alert Triggers` 在 Automations。
   - `Channels` 在 Platform → Integrations。
   - 需要重构为新的一级信息架构。

2. `web/src/components/layout/Sidebar.tsx`
   - 当前根据 Platform subgroup 构建多级导航。
   - 应收敛成一级 Sidebar + Settings Home。

3. `web/src/components/layout/Header.tsx`
   - 清理 JWT / 内部控制台类文案。

4. `web/src/pages/login.tsx`
   - 当前登录体验复杂且存在中文文案。
   - 重做为 Login / First-time Setup 双模式简洁 Shell。

5. `web/src/pages/project-workspace.tsx`
   - 当前存在 `createPlatformAccount()` 和“创建账号”。
   - 平台账号创建迁出 Project，放 Settings → Users & Access。

6. `server/orbisops-app/.../BootstrapAdminConfiguration.java`
   - 当前首任管理员依赖外部 Bootstrap env。
   - 保留高级 headless bootstrap 可选；新增浏览器 First-time Setup 主路径。

7. `web/src/features/platform/pages/PlatformSettingsPage.tsx`
   - 可作为新的 Settings Home 基础，但要从技术子系统分类改为用户可理解的设置分类。

8. Channel 现有后端主链与审批安全语义继续保留，仅重构前端归位和配置体验。

---

## 19. 实施阶段

### P0 — 信息架构与首次使用

目标：先让产品结构正确。

实施：

- Sidebar 用户可见一级导航改为：首页 / 对话 / 工作台 / 工作流 / 自动化 / 项目 / 变更 / 设置。
- 清理旧 Work / Platform / Automations 子层级。
- Header 收敛。
- 用户可见主框架改为中文。
- First-time Setup 前后端。
- Users & Access 页面。
- Project 移除创建平台账号。
- Settings Home 与 Channels 归位。
- 保留旧 URL redirect / legacyPaths，避免直接破坏已有书签和 E2E。

### P1 — Chat ↔ Workbench

目标：建立人工交互与正式任务执行的正确边界。

实施：

- Chat 恢复正常会话心智。
- Project selector。
- Execution selector：Default ReAct / Published Workflow。
- Conversation ↔ Run 关联。
- Workbench unified run query / projection。
- Workbench list / detail。
- Continue in Chat。
- Automation / Workflow 正式 Run 统一进入 Workbench。

### P2 — Workflow / Automation / Project 收敛

目标：明确“怎么执行 / 什么时候执行 / 在哪执行”。

实施：

- Workflow 一级入口。
- Workflow Builder 沉浸式。
- DIRECT / LLM / REACT 固化。
- Workflow version / publish / usage references。
- Automations 统一 Trigger → Project → Execution → Policy。
- Project 收敛为 Overview / Resources / Capabilities / Members / Runtime。

### P3 — Changes / Home / 收尾

目标：生产治理和日常入口完成闭环。

实施：

- Changes 统一治理 Inbox。
- Approval / Landing / Verification 收进 Change detail。
- Home 改为 Needs Attention / Running / Activity / Quick Actions。
- Channel 运行结果与 Workbench / Changes 正确联动。
- 清理旧页面、重复入口、无用路由和中文用户文案。

---

## 20. 验收标准

### 20.1 产品层

必须能用一句话解释一级导航：

```text
Chat = 和 Agent 交流
Workbench = 管任务和 Run
Workflows = 定义怎么执行
Automations = 定义什么时候自动执行
Projects = 定义在哪执行、能用什么
Changes = 管生产变更
Settings = 管平台
```

如果任何页面不能清晰归入以上心智，需要重新审视。

### 20.2 核心 Journey

#### Journey A — 普通 Chat

```text
Chat
→ choose Project
→ Default ReAct
→ ask question
→ answer / evidence
→ no unnecessary Workbench task required
```

#### Journey B — Chat 手动 Workflow

```text
Chat
→ choose Project
→ choose Published Workflow
→ Run
→ durable execution
→ Workbench can track official Run
→ result returns to Chat
```

#### Journey C — Automation

```text
Automation Trigger
→ Project
→ Default ReAct or Workflow
→ Run
→ Workbench
→ result / failure visible
```

#### Journey D — Workbench takeover

```text
Workbench
→ open Run
→ inspect execution / evidence / result
→ Continue in Chat
→ Chat opens with Run context
```

#### Journey E — Production Change

```text
Chat / Run
→ ChangePackage
→ Changes
→ Approval
→ Landing
→ Verification
→ result reflected back to source Run
```

#### Journey F — First Startup

```text
fresh database
→ open OrbisOps
→ First-time Setup
→ create first admin
→ auto sign in
→ Settings → Users & Access can create additional users
→ Project Members only selects existing users
```

#### Journey G — Channel

```text
Settings → Channels
→ configure connection / routing
→ Channel message / event
→ Project routing
→ ReAct or Workflow
→ Run
→ Workbench
```

### 20.3 技术验收

实施完成后至少执行：

```text
Backend compile / architecture tests / full tests
Frontend typecheck
Frontend unit tests
Frontend build
Frontend audit
Critical Playwright E2E
Compose smoke where applicable
Release hygiene / hard-coded path / secret scan
```

必须保护现有用户未提交 README 修改，不得覆盖用户已确认满意的 badge。

除非用户明确要求，不 push。

---

## 21. 非目标

本轮明确不做：

- 重新设计底层 ReAct 架构。
- 重新设计 ChangePackage / Approval / Landing 安全模型。
- 引入 Agent Library。
- 把 Workflow 重新放回 Automations。
- 新增 REVIEW execution mode。
- 将 Tool / MCP / Skill 变成 Workflow Node。
- 为了前端简化而删除 Incident 等已有后端领域能力。
- 自动默认创建硬编码管理员账号 / 密码。
- 把 Channel 做成独立主业务导航。

---

## 22. 最终不可回退原则

1. **Chat 与 Workbench 分开。** Chat 是交流，Workbench 是任务 / Run 控制台。
2. **Workflow 与 Automation 分开。** Workflow = 怎么执行；Automation = 什么时候触发。
3. **Workflow 可被 Chat 手动选择，也可被 Automation / Channel / API 调用。**
4. **Default ReAct 与 Published Workflow 是两种执行绑定，不需要再暴露 Agent Library。**
5. **Workflow Agent Node 只有 DIRECT / LLM / REACT，没有 REVIEW。**
6. **Tool / MCP / Skill / Knowledge 是 Agent Node capability，不是单独 Node。**
7. **Project 是业务、能力和运行安全边界。**
8. **Changes 是生产变更治理入口；Approval / Landing / Verification 属于 Change 生命周期。**
9. **Channels 配置归 Settings → Channels，Channel 运行结果进入 Workbench。**
10. **首次管理员通过 First-time Setup 创建；后续用户由管理员统一管理。**
11. **Workbench 是 Query Projection，不是新 Domain Aggregate。**
12. **前端大改、后端最小配合；不推翻已有 DDD 和安全主链。**
13. **用户可见 UI 统一中文；稳定行业术语按语义保留英文，内部实现概念尽量不暴露。**
14. **任何新页面都必须能映射到明确用户任务，不能因为后端存在一个实体就增加一个 Tab。**

这份文档作为下一阶段 OrbisOps 产品信息架构和前后端实施的权威方案。
