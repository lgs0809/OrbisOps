# OrbisOps 当前开发状态

记录日期：2026-10-03。13:24 UTC 起按用户最新要求暂停剩余大规模功能和正式评测，转向全部页面体验、代码结构及交付整理。此记录保留真实失败和未测，不把源码存在、资源初始化、审批或健康状态当作业务完成。秋招资料只读；README、Git 历史与 push 由主窗口统一处理。

## 当前可运行版本

- 页面：`http://127.0.0.1:3302`；后端：`http://127.0.0.1:18089`。隔离 MySQL 13362、pgvector 15462、模拟资源与 MCP 沿用既有 Compose；保留所有数据库、队列、挂载和用户改动。
- 后端部署17：JAR SHA256 `f1ee57819e256b7053862170064eb78f11ff2aa017115a9d9083ccc7a0118fc5`；源码 `37da21…9671`。完整普通阶段和原生 ArchUnit 共5358项，5330通过、28跳过、0失败/错误。容器2GiB/2CPU、JVM Xmx1024m；不是性能承诺。
- 页面最终UX候选：79文件/270测试、typecheck、发布检查和build通过；正常web-only部署镜像 `sha256:1babd2bd797e8df90b0c050a4b853089e3a2134f0d6118daab0aa03caa3eb88f`，源码摘要 `3e127b01751e990de099f1a491efe793005fe35e98c1eb15ae876082fa7f4965`。25路由分别桌面1442×710及窄屏375×812实际核对；后端未重启。详见[页面体验验收](../acceptance/2026-10-03-页面体验验收.md)。
- 固定小模型 `gpt-5.6-luna`、大模型 `gpt-5.6-terra`。共享 Qwen 由主窗口管理；本文和后续 UI 工作不修改其模型、线程、容器或推理预算。
- 2026-10-03 13:20:05–13:20:45 UTC 单独部署 Landing MCP 读适配器：从实际20次 HTTP、状态和独立 trace 计算 `allSuccessful`。7项真实HTTP反例通过，工具 descriptor、持久calls、mount及backend namespace完全不变；旧image保留。该部署没有改变后端 Java 或目标配置。

## 已执行与仍未完成的业务

| 范围 | 当前真实状态 | 恢复工作时的下一步 |
| --- | --- | --- |
| Workflow 创建和使用 | 已由正常GUI创建、校验、发布 v2 只读 Workflow；正常 Luna 两轮各2个MCP/20实际GET，有SQLite交叉证据。新十五分钟SLO Workflow亦已正常创建发布、自然语言Terra Prepare成功 | 新页面只读核对已有定义、版本和运行；暂停新增模型执行 |
| Prepare / Landing | 新包 `cp-95c2088c-3383-447d-a11d-4176b2417f07` v1/hash4c9a470…由两个独立账号正常GUI审核；唯一CAS将A2模拟资源fixture-4→fixture-5。后续Agent provider404，旧AgentRun保持FAILED；原自动恢复确实执行过，因原工具缺成功字段独立PostCheck失败，package仍LANDING_FAILED | Java明确GUI复核→既有reconcile候选未完整验证部署，不重放CAS、不清SQL标记；新通过后正常GUI复核并保留旧事件 |
| Stage C 完整SLO | generic23原包缺审批前SLO，正确返回INCONCLUSIVE。新A2包已审批前冻结前后900秒/5秒采样/100请求、0.5–2QPS、成功率/错误率/p95及相对边界，但未完成C/TaskAcceptance | 从原operation权威时间和已批准标准读取实际窗口；当前不新增SLO实验或放宽条件 |
| Recovery | 本轮尚无独立批准、实际恢复和最终业务验收闭环 | 之后按现有原生审批与恢复边界执行，不能把Landing成功替代Recovery |
| Skill 自动沉淀与进化 | 20个真实独立来源Accepted，只有一种条件。Source22第二条件原业务执行成功，但完整9318字来源的Luna草案8次240秒失败，仍UNKNOWN/未Accepted。本轮CREATE/PATCH、READY索引、自动启用、正常复用/反馈回退未完成 | 统一660秒前台草稿预算候选通过聚焦但未完整部署；部署验证后复用原Run完整证据。旧灰度/固定20观察门禁已取消，不重新引入 |
| Episode、巡检owner和告警关联 | 已有生产实现及既有回归：24小时暂定结尾/晚消息再分析、巡检归属创建者沿链、通用因果关联与保留原始告警。当前新Jar的完整GUI+DB复验尚未完成 | 只读页面检查不能冒称本轮跨链验收；之后按原任务恢复 |
| Skill 检索容量 | 10000授权行/1000ms预算在full19及聚焦2均真实超时，不能把此前组件通过当当前稳定性证明；候选native数组保留权限、模型、READY、版本和预算 | 保留原失败，继续真实计划/冷读诊断，不降低10000数量、权限或预算断言 |

20个来源仅是本次数据真实性检查，不是新的发布门槛。普通CREATE/PATCH仍依既定3条独立来源、2种有意义条件；真实模型SPLIT/MERGE正向及恢复也尚未验收。

## 测试和暂停记录

- full18：5331总项/5301通过/2断言失败/28跳过；原生架构未执行。原失败保留。
- full19：12:03:59–12:30:49 UTC，自然退出1。新鲜1470 XML，5363总项/5334通过/0断言失败/1真实Pg超时/28跳过。3份旧XML/32项排除。原八worker及32轮并发断言通过，真实300秒lease本轮未显式启用而跳过。没有新合格JAR，未部署。
- 聚焦2：13:15:58–13:20:55 UTC，自然退出1。44总项/43通过/1Pg错误/0断言失败/0跳过；Landing流程27通过、实际MySQL观察时间10通过、Pg6通过/1错误。完整XML、源码hash与log已归档。
- 新Java候选包括统一前台草稿deadline、MySQL固定锁顺序、Pg授权数组及明确复核→reconcile/current-run fence。均保留工作区；最终完整双引擎回归和新Java部署尚未完成，不能因暂停宣称完成。
- 当前已启动的第三段只读流量按原1800秒边界自然结束，不追加实验。真实PID40161于13:10:12 UTC启动；前段12:58左右结束至此的约12分钟无生成器间隔保留。不能声称连续无间断负载或完整SLO通过。
- 正式冻结240题分层定义保留，实际新轮：MCP72/200通过、128未执行；Skill结构50已执行，49评分44通过/5质量失败，1已执行未评分、150未执行。巡检/调查同题方案×5和Prepare/Recovery60题正式模型闭环未完成；旧评分器版本和已见holdout状态不覆盖，不改Gold。
- 当前无新的模型/正式矩阵/Maven任务。新的UI验证、普通组件回归和部署按独立批次记录，不计入业务/模型性能分母。

## 证据与正常恢复入口

原始证据保留于主工作目录 `work/ops-finish-20261003-*`，重点：`server-package-19-summary-verified-1.json`、`pg-landing-focused-2.{json,log}`与3份原XML、`landing-observation-adapter-deploy-1.{json,log}`、`fresh-slo-package-reviewer{1,2}-native-1.json`、`fresh-slo-landing-provider404-1.log`、`fresh-slo-package-recovery-observe-1.json`、`fresh-slo-manual-postcheck-original-native-1.json`及截图。

可重跑入口保持在仓库，操作前检查当前自然在途并保留数据：

```sh
# 后端完整普通install + 新JVM原生ArchUnit；必要时显式启用真实300秒lease
python3 scripts/run-full-acceptance-tests.py --real-lease-wait \
  --java-home /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  --output-prefix /tmp/orbis-full-fresh
python3 scripts/verify-full-acceptance-tests.py \
  --coordinator /tmp/orbis-full-fresh-coordinator.json --output /tmp/orbis-full-fresh-reviewed.json
# 只有完整通过的源/JAR/XML绑定允许正常Java部署
python3 scripts/deploy-tested-acceptance.py \
  --test-log /tmp/orbis-full-fresh-ordinary.log --test-summary /tmp/orbis-full-fresh-reviewed.json \
  --output /tmp/orbis-deployment-fresh.json
# 只读MCP组件部署，先真实HTTP反例再核对descriptor/原挂载和durable calls
python3 scripts/deploy-landing-observation.py --output /tmp/orbis-landing-read-fresh.json
# 页面完整验证；不调用模型
cd web
NODE_OPTIONS=--max-old-space-size=1536 npm run verify
```

恢复后三阶段审批必须使用未过期、同一版本/hash/当前run的原生证据；禁止事后添旧包标准、造成功状态、把机械重复算独立来源。隔离环境沿用现有种子入口，碰到已有不同资源状态拒绝覆盖。

## 暂停后自然结束的在途进程

原已启动的 traffic-live-3 于 2026-10-03 13:40:12 UTC 自然结束（exit 0），实际 1728 个 HTTP 请求均有 traceId，均返回 200 / fixture-5。原始时间范围 13:10:13–13:40:12 UTC；此前约十二分钟流量空窗仍保留。这只证明模拟业务资源的实际请求，不代表 SLO / TaskAcceptance 已通过。完整记录及 summary 保存在上述 work 目录，未启动新流量。

## UX收口与Java小验证

2026-10-03 14:21:37–14:22:02 UTC，用户当前交付整理范围内的小Java focused自然exit0：仅 `ChangePackageLandingProcessManagerTest` 27/27通过，含新7个复核/身份/独立PostCheck边界；使用2CPU/512MiB编译现有reactor依赖，源码前后相同。未启动PG/MySQL/Testcontainers、模型、SLO、流量或后端部署。此结果确认候选单元合同，不解决full19 Pg容量错误，不替代完整双引擎回归，也不宣称在Jar17中已生效。

UX统一导航、返回分类、工作区高度、加载/错误展示，并抽取侧栏样式、设置分类模型、变更列表和项目页独立投影查询。真实画布越界与SDK默认缩放裁边在浏览器发现后修复，重新270测试、构建和正常web部署；最终25×2路由复读及新画布检查通过。只新增一个绑定v2的空手测会话，MySQL消息数0；无新模型或工具执行。浏览器临时375尺寸已reset1442×710。

未完成业务矩阵、来源第二条件、Skill生成/发布/复用、StageC/TaskAcceptance及Recovery仍按上文暂停。README与Git发布由主窗口统一处理，本批未commit/reset/push或再次改历史。
