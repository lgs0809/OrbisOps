# OrbisOps 本地实施与验收记录

日期：2026-09-08。当前交付批次：OPS-01。OPS-02 已开始核对，OPS-03～OPS-08 尚未宣称完成；其他项目本批未修改。

## 结论与边界

**OPS-01 的代码、隔离部署、有业务关联的数据、自动化回归、真实 MySQL/PostgreSQL 测试和真实 JVM 宕机恢复已通过。真实模型抽取、模型摘要及 embedding 效果受阻，未计为通过。**

本次浏览器和 API 的默认助手任务因为本隔离环境关闭模型调用而失败。它们只用于验证失败记录、历史消息、摘要和重放，不代表运维分析成功。没有制造已审批、已发布或任务成功状态；本批也没有执行变更审批与发布业务验收，这些属于后续 OPS-02/04/08。

仓库开始时为干净 master，基线 2749fa8。未提交、推送或修改原秋招资料。本批新建端口全部绑定 127.0.0.1；新建 Compose 项目及命名卷与原环境隔离，未删除数据或数据卷。启动 Docker Desktop 时，原有容器依其已有重启策略恢复运行；本批故障注入仅针对 orbisops-acceptance-backend-1。

## 已完成的代码

| 任务 | 落地内容 | 核心文件（仓库相对路径） |
|---|---|---|
| OPS-01 消息顺序 | 在 MySQL 事务中锁定会话状态，分配 messageSeq，原文与三个后处理事实一并提交；turn/role 幂等，错误项目绑定拒绝 | domain/memory 的 IConversationMemoryRepository、ConversationMemoryWindow、MemoryProcessingJob；infrastructure/adapter/repository/JdbcConversationMemoryRepository；application/memory/MemoryCaptureApplicationService |
| OPS-01 压缩一致性 | summaryRevision + coveredSeq 条件提交、不可变摘要版本；继承前一摘要；完整保存硬约束/未完成事项原文引用；不替换或删除并发尾部 | MemoryCompressionPolicy、MemoryCompressionApplicationService、MemoryContextRenderingApplicationService |
| OPS-01 恢复 | 读取时从 MySQL 重建会话窗口；后处理持久化领取、租约和 epoch、失败重试；本地抽取效果与完成状态同事务；定时重放 | MemoryRetrievalApplicationService、MemoryPostProcessingApplicationService、OpsMemoryPostProcessingJob、ColdMemoryStoreApplicationService、ContextMemoryStoreApplicationService |
| OPS-01 去重与隔离 | 规范化内容指纹排除版本；审计 Hash 和幂等键分开；逻辑键事务锁；相同事实增加来源记录，不新增冲突版本；projectId 从可信请求贯通 | GovernedMemoryHashPolicy、GovernedMemoryApplicationService、JdbcGovernedMemoryRepository、OpsRuntimeConversationContextCoordinator |
| OPS-01 投影 | 稳定消息文档 ID；PostgreSQL lexical upsert；语义召回核对仍存在的 MySQL 原文；清空后取消任务并保持序号单调 | SemanticMemoryWriteApplicationService、OpsSemanticLexicalWriteAdapter、OpsSemanticVectorWriteAdapter、JdbcColdMemoryRepository |
| 本地部署缺陷 | 不改历史迁移校验和，修复 baseline 写死数据库名；无 baseline 历史的非空库拒绝初始化；新增 077 | server/scripts/db-migrate.sh、server/db/migrations/sql/ops-memory-durability.sql、manifest.tsv |
| 页面缺陷 | 管理员在空项目页面也可创建项目，校验名称与标识并刷新项目选择 | web/src/pages/project-product-workspace.tsx |

完整修改清单见 changed-files.txt。Java 路径分别位于 server/orbisops-domain、server/orbisops-application、server/orbisops-infrastructure、server/orbisops-trigger 的 src/main/java/cn/lgs/orbisops 下。

## 实际测试命令与结果

在本仓库根目录执行（mvn 命令在 server，npm 命令在 web）：

| 命令 | 结果 | 证据 |
|---|---|---|
| 基线：`mvn -B test` | 各模块合计 4522 项，0 失败，0 错误，5 跳过（app 模块 4366） | server-test.log |
| `mvn -B test` | 各模块合计 4532 项，4521 通过，0 失败，0 错误，11 跳过（app 模块 4376） | server-test-verified.log |
| `npm run verify` | 47 文件 / 133 测试通过；类型、发布卫生、生产构建通过 | web-verify-final.log |
| `python3 scripts/test-memory-integration.py` | 真实 MySQL + PostgreSQL 7 项通过，无跳过 | mysql-pg-memory-tests-verified.log |
| `python3 scripts/test-migration-guard.py` | 对带现有业务表的独立测试 Schema 拒绝 baseline，退出码 6；原数据保留且未记录假迁移成功 | migration-guard.log |
| `python3 scripts/test-memory-runtime.py` | 实际领取前宕机、领取后宕机、连续摘要与重启恢复三个场景通过 | runtime-crash-tests.log |
| `python3 scripts/seed-local-acceptance.py`（两次） | 实体不重复，既有成员角色不变，真实资源扫描 SCANNED，关联行数一致；跨项目/管理接口拒绝，数据库写拒绝 | seed-repeat.log |
| `python3 scripts/local-acceptance.py up` | 镜像构建、迁移 077、前后端及依赖启动成功 | deploy-final.log、deployment-state.tsv |
| `git diff --check`、Python 脚本语法编译 | 通过 | 本地命令结果 |

后加的 PostgreSQL 测试独立执行通过；全量 4532（其中 app 4376）统计来自它加入之前的一次全量运行，不能把两份数字直接相加当作不重叠测试数。

真实数据库测试包括：40 次并发 capture（20 个幂等请求）、相同幂等键改内容拒绝、A/B 绑定拒绝、两次压缩中途追加及逆序完成、过期 epoch 拒绝、抽取事务失败完整回滚、重放不重复效果、20 个并发来源保留同一个事实版本、清空后迟到投影不可召回、PostgreSQL 重复写入只有一个稳定 ID。

## 真实运行与浏览器证据

- Computer use 完成：初始化管理员（正常 setup 页）、创建项目 A、检查资源/成员、发送包含硬约束和待确认事项的请求、打开失败运行及技术轨迹、登出后用只读账号登录并展开项目列表。
- UI 会话：chat-session-202519b9-0b3b-49fb-b4c5-b9c88e8e865f。页面、数据库和运行事件均显示模型未启用导致失败；消息原文依旧持久化。
- 领取前宕机：chat-session-5430d19f-4525-43aa-9e6b-df758f7e76c6。6 条 PENDING 记录在 JVM 终止后保留，重启后全部自动补处理，抽取结果 1 条。
- 领取后宕机：chat-session-5d64865a-5494-45b1-a2f9-34cd5293335c。领取事务已提交，3 条 RUNNING；JVM 终止后等待真实 5 分钟租约到期，自动以 attempts=2 重放，抽取结果仍为 1 条。测试触发器全部清理，未改库缩短租约或伪造完成状态。
- 连续压缩和重启：chat-session-f16a3a16-40b8-46ca-870c-09dd9b9c691b。44 条消息形成 revision 1/2/3，覆盖至 seq 12/23/34。真实 backend 重启后继续到 seq 46，实际 runtime context bundle 仍包含“禁止重启订单服务”及完整待确认原文。
- 本批快照：54 条消息对应 162 条后处理记录，均 COMPLETED；其中 54 条 semantic 的 resultKind 为 DISABLED，不能解释为向量写入成功。抽取 EXTRACTED=3、NO_FACTS=51，压缩检查 CHECKED=54。
- 截图：01-project-members.png、02-chat-model-blocked.png、03-run-failed-evidence.png、04-viewer-project-scope.png。仅含合成验收账号/数据，不含密码与 token。

## 数据与访问

| 项目 | 可访问账号及角色 | 业务库 | 数据 |
|---|---|---|---|
| ops-acceptance-a | ops_acceptance_operator / MAINTAINER；ops_acceptance_viewer / VIEWER | ops_acceptance_business_a | 3 服务、6 虚构客户、12 订单 |
| ops-acceptance-b | ops_acceptance_b_member / MEMBER | ops_acceptance_business_b | 3 服务、6 虚构客户、12 订单 |

服务含 HEALTHY、FAULT、INSUFFICIENT_DATA 三种合成场景标识；订单含 PENDING、PAID、CANCELED、FAILED 和零金额/最小金额等边界。这里只准备了关联业务事实，尚未搭建 OPS-04 的可查询时序指标/日志目标，不能把场景标签当作已跑通三类运维图。

两个库使用不同的只读账号。实际 INSERT 被 MySQL 1142 拒绝。用户只能列出各自项目；访问其他项目 agent catalog 和管理员接口均返回 403/0005。资源通过正常接口实时扫描表结构，保存环境变量引用，无明文资源密码。

- UI：http://127.0.0.1:3302
- API：http://127.0.0.1:18089
- MySQL：127.0.0.1:13362；PgVector：127.0.0.1:15462；Redis：127.0.0.1:16362。
- 私密凭据仅在仓库 deploy/.env.acceptance、deploy/.acceptance-private/admin.json、users.json；权限 600，均忽略提交。报告与日志未附凭据。
- 初始化固定前缀 ops-acceptance / ops-acc，业务数据基准时间 2026-09-08 12:00:00，无随机业务行；回归测试每次创建明确标注的独立合成会话，不覆盖原业务数据。

## 可重跑步骤

```sh
python3 scripts/local-acceptance.py up
python3 scripts/seed-local-acceptance.py
python3 scripts/seed-local-acceptance.py --verify-only
python3 scripts/test-memory-integration.py
python3 scripts/test-migration-guard.py
python3 scripts/test-memory-runtime.py
```

首次可从浏览器正常 setup 页面创建账号，并将本地验收账号保存在私密文件；也可由 seed 脚本调用同一个 setup API。已有管理员且没有对应私密登录资料时，脚本会失败，不覆盖账号或重置密码。

`test-memory-runtime.py` 明确会终止并重启新增隔离 backend，两次故障会临时创建仅匹配测试会话的延迟触发器，finally 删除并启动服务；领取后恢复等待默认五分钟租约。不要在其他环境改端口/容器名运行。停止隔离环境用 `python3 scripts/local-acceptance.py stop`，保留全部卷。脚本没有 down -v、清库或生产部署步骤。

## 失败、未测与受阻

**曾失败，已修复并重测通过**：新增摘要/去重回归先复现旧代码缺陷；旧内存计数/缓存替换/SQL 参数断言适配新契约；baseline 写死 orbisops；空项目页面缺创建入口；PG 实测首次被本机 Maven SOCKS 设置错误代理数字回环地址，测试进程现显式绕过回环代理（未改变系统代理）。对应原失败日志一并保留。

**实际业务任务失败**：隔离环境关闭模型调用，默认助手运行未生成可用分析结论。此失败状态保留在 UI 和数据库；不能纳入运维成功率或模型效果。

**未测**：后续 OPS-02～08 的关键长图、业务 A/B/C 图、变更审批/发布/完成验收、Episode 与 Skill 演进/检索评测。本批全量跳过的原有用例为 3 项真实 LSP（Pyright/JDT LS/双 LSP）、1 项 prod-like landing、1 项真实模型 relay；另 6 项 MySQL 用例因通用全量命令未设置环境变量而跳过，已通过专用集成命令真实执行，后来新增的 PG 用例也已真实通过。

**受阻**：本隔离环境未导入原环境 Provider 密钥并显式关闭模型调用，LLM 抽取/摘要及 embedding/reranker 的真实效果未验收。没有用固定模型成功响应补齐，也没有迁移用户既定模型。

**实现限制**：硬约束/待办原文目前采用保守关键词识别，真实模型长上下文效果需要后续验收；受保护原文超出上下文预算会显式失败，不静默裁掉。任务异常八次后保留 FAILED，需要人工检查后显式重放；尚未添加运维重放页面。
