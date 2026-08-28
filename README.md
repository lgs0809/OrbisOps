<div align="center">

# OrbisOps

**让运维调查、自动化执行和生产变更进入同一条可审计流程。**

自然语言调查 · 可视化 Workflow · MCP · 三阶段治理 · Skill 演进

[![Java](https://img.shields.io/badge/Java-17-ED8B00)](server/pom.xml)
[![React](https://img.shields.io/badge/React-18-149ECA)](web/package.json)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue)](LICENSE)

[快速开始](#快速开始) · [架构](#架构) · [开发与验证](#开发与验证) · [当前状态](#当前状态)

</div>

OrbisOps 是面向运维工作的 Agent 控制台。用户用自然语言说明问题，系统在项目权限范围内调查、调用工具和整理证据；重复流程可以沉淀为 Workflow。生产写入须经过变更包、审批、受控执行和结果验收。

## 使用流程

1. 创建项目，绑定模型、测试与生产 MCP、执行目标和知识资源。
2. 从对话开始调查，或在可视化编辑器创建、校验并发布 Workflow。
3. 在工作台查看步骤、工具调用、任务和证据，补充必要的业务条件。
4. 确认 Prepare 产生的变更包；按规则审批后进入 Landing。
5. 分别记录执行、PostCheck、Observe、Recovery 结果，通过正常任务验收。
6. 后台按 Episode 分析工作，形成可审核的 Skill 创建或修订候选。

![OrbisOps 工作台](docs/assets/product-overview.png)

## 核心能力

| 领域 | 能力 |
| --- | --- |
| 调查与执行 | ReAct 对话、Workflow、版本发布、运行记录与原始证据 |
| 资源与权限 | 项目隔离；模型、MCP、Skill、知识库与执行目标管理 |
| 变更治理 | Discovery → Prepare → Landing；包版本、审批、执行与验证绑定 |
| 任务与告警 | 定时巡检、归属传递、重复去重与事件关联，保留原始告警 |
| 经验沉淀 | Episode 分段、24 小时暂定结尾、晚到消息重新分析、后台持久重试 |
| 工作台 | 会话、任务、告警、变更、审计和设置，共享布局与导航契约 |

测试与生产环境分别通过 MCP 接入资源。Discovery / Prepare 保持调查权限；生产非只读能力只在满足审批及包版本约束的 Landing 执行范围内开放。模型和页面按钮均不能自行授予生产写权限。

## 架构

```mermaid
flowchart TD
    U[浏览器 / 告警 / 定时任务] --> R[对话与 Workflow 运行时]
    R --> P[项目资源与权限]
    P --> T[模型 / MCP / Skill / 知识检索]
    R --> C[ChangePackage]
    C --> A[审批与版本校验]
    A --> L[Landing 执行器]
    L --> V[验证 / 观察 / 恢复 / 任务验收]
    R --> E[Episode 后台分析]
    E --> S[Skill 候选与发布]
```

Java 多模块区分领域规则、应用服务、基础设施和入口；React 使用统一工作台布局及服务端状态。MySQL 保存业务记录，PostgreSQL / pgvector 保存知识与记忆向量，Redis 承担协调。外部资源通过明确的 MCP 和执行器契约接入。

## 快速开始

需要 Docker Compose v2。源码与 CI 使用 Java 17、Node.js 24 与 npm；验收脚本会记录实际选择的 JDK。

```sh
git clone https://github.com/lgs0809/OrbisOps.git
cd OrbisOps
cp deploy/orbisops.env.example deploy/orbisops.env
# 在本机填写基础设施和认证密钥，替换占位值
make deploy-preflight
make up
```

默认页面为 **[http://127.0.0.1:3002](http://127.0.0.1:3002)**，API 为 **[http://127.0.0.1:8099](http://127.0.0.1:8099)**。首次从页面创建管理员，再创建项目并配置实际模型与 MCP。模型服务不会由仓库自动下载或启动。

原隔离验收入口为 **[http://127.0.0.1:3302](http://127.0.0.1:3302)**，API 为 **[http://127.0.0.1:18089](http://127.0.0.1:18089)**。关联数据、初始化脚本和检查 SQL 见 [验收目录](deploy/acceptance/) 与 [开发状态](docs/development/OrbisOps-当前开发状态.md)。初始化冲突拒绝覆盖；审批、发布和验收通过正常流程执行。该验收应用本批暂停，当前展示入口为3304。

```sh
# 停止应用，保留持久卷
make down
```

## 开发与验证

```sh
make release-hygiene
cd server && mvn -B test
cd ../web && npm ci && npm run verify
```

完整隔离验收使用 `scripts/run-full-acceptance-tests.py`，受测产物部署使用 `scripts/deploy-tested-acceptance.py`。先阅读参数及 [验收状态](docs/development/OrbisOps-当前开发状态.md)。普通测试、真实组件、模型质量和业务验收分别记录。

MCP 发现实际复用 Spring AI 1.1.4、Alibaba 1.1.2.2 与社区 Tool Search 1.0.1 适配；图循环仍由 Alibaba 管理。摘要预算使用显式标记的 JTokkit/O200K_BASE 估算，运行时冻结计数依据和搜索上限，不宣称已与主模型 tokenizer 等价。迁移、恢复和复测说明见 [MCP 计数契约](docs/development/mcp-discovery-counting-contract.md)。

```text
server/
  orbisops-domain/          领域模型与规则
  orbisops-application/     应用服务与编排
  orbisops-infrastructure/  存储、模型与外部适配
  orbisops-trigger/         HTTP、任务和运行入口
  orbisops-api/             公开契约
  orbisops-app/             装配、配置与架构检查
web/src/                   页面、共享工作台与资源查询
deploy/acceptance/         隔离部署、种子资料和检查 SQL
scripts/                   验证、部署、初始化及只读核对
docs/                      架构、开发和分批验收记录
```

## 当前状态

10 月 10 日本地完整回归：后端 **5609 通过、6 按环境条件跳过、0 失败或错误**；前端 **435 项测试、40 项浏览器回归通过**，类型检查及构建通过。实际服务端输入、七个模块、JAR 和 45 个前端构建文件已独立核对。真实五分钟租约、万级检索和原性能预算保留。跳过的外部语言服务、真实模型及生产模拟条件单独列出，不能算通过。

账号按手机号登录，新成员由管理员添加。创建时设置初始密码，首次登录必须自行修改；管理员可恢复原初始密码，旧登录令牌立即失效。个人密码修改、管理重置和版本竞争使用统一应用服务及数据库比较更新，既有账号与业务数据保留。

独立展示空间位于 **[http://127.0.0.1:3304](http://127.0.0.1:3304)**，只有三个账号。三个项目各有2026年6月30日至10月10日每天一条排查，共309次，关联156个事件、78个归组、63个变更包及45个沉淀候选。历史明确标为合成展示，模型调用、自动触发和资源执行默认关闭；不能把它们当实际审批、模型或业务验收证明。初始化、重复导入、只读核对及保留数据重启见 [展示空间说明](docs/acceptance/showcase-workspace.md)。

已有 Workflow、三阶段审批和落地的真实页面证据；Source22 正常验收后，后台 Terra 方法提取与 Qwen 索引已实际成功，并有数据库及向量读回。当前新经验组只有一个独立来源，正常返回“重复观察不足”，尚未生成、发布或复用新 Skill。独立来源及条件多样性规则保持。

仍未完成：新一轮完整三阶段 SLO / Recovery / 任务验收、Skill 新候选发布与复用，以及剩余正式评测。本批账号和展示验收通过不替代这些业务闭环；本批仅本地修改与部署，未整理 Git 历史或上传。实际命令、初始化 SQL、运行证据和剩余边界见 [10月10日本批验收](docs/acceptance/2026-10-10-账号与日常展示验收.md) 与 [开发状态](docs/development/OrbisOps-当前开发状态.md)。

## License

[Apache License 2.0](LICENSE)。
