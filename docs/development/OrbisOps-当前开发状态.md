# OrbisOps 当前开发状态

2026-10-10：账号首次登录与管理员重置已完成代码、真实MySQL与HTTP检查，完整后端5615项中5609通过、6原条件跳过，前端435项及40浏览器回归通过。当前独立展示入口3304使用受测JAR fd7465fd…和45份受测页面产物；原3302应用暂停、原库与全部卷保留。只有三名展示用户、一个管理员；三个项目各103天，共309次每日排查。历史为合成展示，不能作为真实审批、Skill自动发布、模型和资源执行证明。旧完整三阶段及新Skill复用未完成项继续保留，不由本批通过代替。详细见[本批验收](../acceptance/2026-10-10-账号与日常展示验收.md)。

2026-10-08 恢复批次已正常部署取消职责收尾版本：源码6f56ba… / 5013文件，原完整install与独立fresh-JVM ArchUnit共5504项 / 5491通过 / 13原条件跳过 / 0失败错误；1481份新鲜XML、7模块安装类与精确JAR4e274318…绑定通过。09:47:39（Asia/Shanghai）正常部署backend镜像110aa1be…；Web仍是原受测13bf。11个同伴镜像 / 挂载与原18业务元组保持，部署后13只读检查全通过。

Registry / State / live listener职责、异常传播和完整模型等待委托已收尾；State统一为每Run不可变信号与ConcurrentMap原子结束 / TTL清理，保留原期限与接口。实际旧实现黑盒RED丢失一次新取消（1/4096，原1P1F保留），过期竞态本轮未复现。新源19类72项及原4类48项真实组件回归均通过；完整中Workflow真实MySQL28 / Skill101 / PG7全执行，真实租约单case304.137秒，原10k / 1000ms预算不变。原70只认证845918，昨日full8三项FAIL继续保留。

当前新版本的真实模型取消与computer use、三阶段新900+900秒业务窗 / 双审批 / Landing / Recovery / 任务验收、Skill发布与复用仍待实际流程核验，不由组件 / 健康 / 部署通过代替。最新状态与可重跑入口见[恢复验收](../acceptance/2026-10-08-local-resume.md)，下文按原日期保留历史。

记录日期：2026-10-07。当前本地恢复工作按用户要求持续执行；此记录保留每次真实失败和未测，不把源码存在、初始化、审批、健康状态或SKIP当作业务完成。秋招资料只读；Git提交、历史整理与上传须等本地工作完成后由主窗口统一协调。

当前已部署候选10（2026-10-07 12:34 UTC）：源码2475de525250a2765b40cd80debe9f9a64270bcc9d39eae796ba0280e418cac0，原full7共5455项/5428通过/27原条件跳过/0失败错误，显式Zulu17、2CPU/512MiB测试预算、真实300秒lease和原PG容量/1000ms不变。普通install与独立fresh JVM架构检查均退出0，实际源码、已编译/安装类与JARf6d98d1f26d736a6536e7fa7f808de9ca1a722179fa0713e6193fcba2bb2f999绑定通过；12:21:03 UTC正常部署该精确产物，backend镜像898bfc0f。11个原运行命名空间同伴、数据库与挂载保持。运行时仍Temurin17.0.20+8/Xmx1024MiB/容器2GiB和2CPU；512MiB是测试预算。Web源码ffc93971未变，原fullWeb8的328项与40 E2E通过、44静态字节核对保持，实际镜像b1839165。部署后server/browser/nginx的660000/680000/690000毫秒合同一致。

Source22已真实GUI自然语言整理64条件、正常确认核验；同Episode revision2 SUCCEEDED、唯一原验收与hash、四份原MCP回执及实际20请求证据逐项核对通过。持久层旧20项上限缺陷的原native红33项/32通过/1失败保留；统一已有MAX64后聚焦64通过及上述完整full7通过。正常后台sweep在部署后捕获同一验收来源、恢复同job，原SKIP_TASK_OUTCOME_UNVERIFIED及patch/hash保留，没有新增独立任务。真实Terra完成方法提取并CREATE新经验组，真实Qwen两次embedding完成；MySQL来源/方法/组版本与pgvector摘要、模型、1024维及独立L2校验7项通过。新组只有1个独立Episode和1种条件，本轮合法SKIP_INSUFFICIENT_REPEATED_OBSERVATIONS；尚未生成、发布或复用新Skill。

原A2的正常GUI复核已通过，最新20次实际只读PostCheck与native记录核对通过。部署后只读确认仍LANDED、一个原attempt、唯一原写回执、两独立审批、批准hash及fixture5 HEALTHY保持；没有重复CAS。仍未验收：新5→6的canonical Prepare、正常双审批、Landing及独立安静900+900秒业务窗、Stage C/Recovery/任务验收，Skill新候选发布与复用、正式矩阵剩余项及真实OBSERVER账号正例。原C FAILED/NOT_REACHED、模型传输失败、旧23次流量运输超时、旧测试失败与中断均保留，不能由本批回归和部署替代。

详细可重跑命令、SQL、截图和分批证据见[2026-10-07恢复验收](../acceptance/2026-10-07-恢复验收.md)。下列10月3日暂停段及后续在途段按原时间保留，当前身份以本段为准。

## 2026-10-03 历史可运行版本

- 页面：`http://127.0.0.1:3302`；后端：`http://127.0.0.1:18089`。隔离 MySQL 13362、pgvector 15462、模拟资源与 MCP 沿用既有 Compose；保留所有数据库、队列、挂载和用户改动。
- 后端部署17：JAR SHA256 `f1ee57819e256b7053862170064eb78f11ff2aa017115a9d9083ccc7a0118fc5`；源码 `37da21…9671`。完整普通阶段和原生 ArchUnit 共5358项，5330通过、28跳过、0失败/错误。容器2GiB/2CPU、JVM Xmx1024m；不是性能承诺。
- 页面最终UX候选：79文件/270测试、typecheck、发布检查和build通过；正常web-only部署镜像 `sha256:1babd2bd797e8df90b0c050a4b853089e3a2134f0d6118daab0aa03caa3eb88f`，源码摘要 `3e127b01751e990de099f1a491efe793005fe35e98c1eb15ae876082fa7f4965`。25路由分别桌面1442×710及窄屏375×812实际核对；后端未重启。详见[页面体验验收](../acceptance/2026-10-03-页面体验验收.md)。
- 固定小模型 `gpt-5.6-luna`、大模型 `gpt-5.6-terra`。共享 Qwen 由主窗口管理；本文和后续 UI 工作不修改其模型、线程、容器或推理预算。
- 2026-10-03 13:20:05–13:20:45 UTC 单独部署 Landing MCP 读适配器：从实际20次 HTTP、状态和独立 trace 计算 `allSuccessful`。7项真实HTTP反例通过，工具 descriptor、持久calls、mount及backend namespace完全不变；旧image保留。该部署没有改变后端 Java 或目标配置。

## 2026-10-03 历史业务状态

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

## 2026-10-07 本地恢复批次

本地恢复已完成新的完整后端验证与正常部署：5401总项/5374通过/27跳过/0失败及错误；新JVM原生ArchUnit、真实300秒lease、八worker/32轮并发与原10k授权Skill检索预算均通过。已有native数组实现不再重复重构。最终前端301项、原40项E2E、构建与正常部署通过。

原A2于04:15:02 UTC通过真实GUI复核，独立PostCheck共40实际只读GET，正常reconcile将原包置LANDED、原LandingRun置SUCCEEDED。原CAS仍仅一次、fixture-5状态与写回执未变，旧AgentRun FAILED和旧provider404保留。页面当前版本/运行账本优先于过期APPROVED视图，未知或已执行操作不提供重复提交。

原A2自动C继承operator及v6/hash，正常到期04:30:02，不改原10月3日窗口；旧流量空档不能由新流量补齐。本批开始新正常1QPS只读观测；新可比前后900秒必须属于独立正常审批的变更。Source22继续用原Run与75ms真实20条证据，在部署后的660秒预算下实际GUI整理条件，确认前仍UNKNOWN。Skill生成/索引/启用/复用及Recovery尚需具体业务结论，正式未执行矩阵保留。

详细命令、精确产物哈希、通过/失败/未测边界和本机证据入口见 [2026-10-07恢复验收](../acceptance/2026-10-07-恢复验收.md)。本批仍没有commit、历史重写或push；README和远端由主窗口协调。

恢复验收后续发现：原自动C为FAILED/NOT_REACHED，after重复包装1224338字节超出原1MiB；原MCP调用和回执成功不等于业务review通过。Source22浏览器260秒提前取消，nginx499，原代理360秒与服务端660秒错配，任务仍UNKNOWN。候选统一服务端预算合同→浏览器→代理部署核对，并在受权MCP工具边界使用typed ToolContext投影、保留完整不可变回执；通用数据上限、权限、计数不变。前端预算聚焦14/14通过，后续服务端完整回归和部署等待独占窗口。完整失败和旧记录保留。

08:03 UTC当前：source4完整full3已通过5387项、27跳过、0失败/错误，fresh双JVM/实际300秒lease/源码与精确JAR绑定；新通用执行计划保护形成候选5，未Java测试/未部署。新自然语言Prepare两包尚未审批，生产无新写；必须继续最终候选验证、真实Source22验收/Skill及新5→6的完整前后900秒，不能把首批部署或Prepare成功当最终业务闭环。

08:21 UTC新候选5为16b989342d7fcafa455b1c897fcf2a86fb514a0472587243ed591fe4b33ee32b；通用计划guard与仅基于原完成journal的只读复核兼容已写入，尚未Java测试/部署。Source4通过仍仅对应Source4。原A2单写事实完整保留，没有新生产写、审批或Git操作。

08:32 UTC 候选5源08801578聚焦14类/124通过；第一次fixture ERROR保留、负例已实际到recorded-view。原full4执行中，旧部署未变，新功能尚未真实部署验收。

## 2026-10-08 当前本地验证边界

稳定6f56源码的5491通过 / 13跳过完整回归已正常部署为4e274 JAR。真实浏览器v22新Workflow因extension本Run未激活自然失败，尚未到达模型和停止动作；原失败保留。随后只读意图映射、条件激活及安全失败文案形成989833候选：36类199项聚焦全部通过，原15安全用例未改；原full2失败触发的职责问题已修复，但原full3尚未运行，新代码未部署。前端8de0候选362组件和原40 E2E全部通过，尚未部署。详细红绿记录、完整源码及产物身份、实际SQL和未测项见2026-10-08-local-resume.md及主工作目录work/resume-20261008/orbisops。README、Git和远端由主窗口统一处理。

## 2026-11-02 计数与统计一致性

OPS-01/02 保留 JTokkit 估算并明确 ESTIMATE 身份，通过迁移104冻结实现/版本/模型/编码/20/2000/5；修复恢复使用当前搜索上限的问题。Skill 后置 success/evidenceSufficient 事实现与聚合在原CAS事务中一起补差。完整门禁 c7aa68b 源已正常部署为0f37bcfe JAR；随后仅修正隔离 Workflow 测试故障注入的数据库绑定，44项真实DB补跑通过，全部生产类与JAR字节仍一致，前端435单测/40夹具E2E验证后正常部署。CUA普通中文+实际Luna完成48目录工具搜索/按需Schema/一次只读MCP调用，与DB/SQLite/哈希交叉核验。新代码及真实验收范围见[2026-11-02验收](../acceptance/2026-11-02-mcp-counting/验收记录.md)。组级活动调查复用、正式评测及三阶段/Skill新业务矩阵仍单列未完成，没有以本批目录链路代替。保留原用户改动，无Git提交或推送。
