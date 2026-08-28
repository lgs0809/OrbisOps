# 2026-10-08 本地恢复验收

本批先收尾 OPS-02 / OPS-03 的取消执行职责。所有业务审批、发布和任务验收仍走正常入口；原数据库、数据卷、失败记录及用户工作区保留。Git、README 与远端由主窗口统一协调，本批不提交或改引用。

## 恢复时的实际版本

后端实际 JAR 为 `f6d98d1f26d736a6536e7fa7f808de9ca1a722179fa0713e6193fcba2bb2f999`，镜像 `898bfc0f…`；地址 `http://127.0.0.1:18089`。页面 `http://127.0.0.1:3302` 保留原受测 Web `13bf1248…`。此次只读 13 项检查通过，18 条业务元组保持原样：A2 一次落地及两独立审批、唯一原回执和 fixture-5；Source22 rev2 成功及四份完整回执；旧恢复审查状态；OBSERVER 零消息、零 Run。未调用模型或创建新业务运行。

昨日取消候选完整 full8 为 5448 通过、3 失败、13 原条件跳过、0 错误。未部署该候选。失败日志和原报告继续保留，不由今日源码存在或聚焦测试覆盖为成功。

## 本批职责收尾

`OpsRunCancellationRegistry` 只协调保留状态和活跃监听。`OpsRunCancellationState` 统一原 null / blank / trim 行为与保留期限，每个 Run 只保存一份不可变取消信号；结束和过期清理经 `ConcurrentMap.computeIfPresent` 原子更新，避免独立集合交错擦掉新信号。`OpsRunCancellationListeners` 管理连接 handle 的注册、单次触发和同步幂等关闭，不保存 Run 取消元数据或 TTL。公开 `CancellationRegistration` 类型和无需 checked exception 的 close 使用契约保持。

模型等待继续委托已有 `OpsModelCallAwaiter`。架构回归同时核对 Executor → Awaiter → 原 JDK timed get / await、可信监听注册与资源注销、明确的取消异常和原时间预算；原 facade / deadline / timeout / trace / 线程中断 / 框架边界均保留。Registry 的 70 行限制及 LLM Client 的 210 行限制没有放宽；相同行为的四种同级异常使用 multi-catch 传播，取消仍不能进入降级。

新增 8 项公开接口及并发回归，包含 32 轮并发注册 / 取消，空白输入，trim，线程中断与持久取消的区别，保留期限重新开启，typed handle 的幂等关闭，以及关闭返回后不能继续触发旧 handle。

## 状态与下一步

当前源码冻结 `6f56ba9025d24e599c5b969510684a2d3b9d2dc0d25f2f9f640de3584f026031`，5013 份源文件。2026-10-08 09:02:25–09:03:00（Asia/Shanghai）实际 19 类 / 72 项聚焦全通过，0 失败、错误或跳过，Maven exit0 / 34.541 秒；19 份新鲜 XML、源码前后 hash 和原 release17 编译类核对通过。原 12 类取消 54 项、State / Settings 3 项、新兼容 8 项、三个架构类的 5 项，以及新并发状态 2 项实际执行。原 Socket / header / body / producer 取消预算和拒绝场景均保持；此批 parent512 / fork384 MiB / 2CPU，不调用真实模型或原生数据库。

原 `845918…` 的 18 类 / 70 项通过只认证职责拆分前一候选。后续两个黑盒并发用例各4096轮，09:01:11–09:01:37在旧双状态实现实际获得 1P / 1F：`finishingAnUncanceledRunCannotEraseConcurrentCancellation` 丢失一次新取消信号。过期清理竞态本轮未复现，不冒称两种均已复现。原 Maven exit1、XML、日志和生产源保留；单原子状态修复后两个用例及原18类重新完整运行通过，没有删除反例或改变保留期限。

聚焦阶段未将结果当作完整回归、安装 JAR 或真实业务通过；后续仅在独占窗口执行以下完整测试和部署。共享重测试窗口由主窗口协调，不能并行启动三个仓库的 Maven、真实组件或模型测试。

09:25:19–09:26:23 实际原 4 类 / 48 项 checkpoint / MySQL 全通过，0 失败、错误或跳过，其中 Workflow 的 28 项全部使用真实 MySQL。4 份新鲜 XML、同一冻结源码和真实 JVM17 / parent512 / fork512 MiB / 2CPU 核对通过；本次日志中的 28 个唯一 synthetic Run 的 96 行原生记录与只读 SQL 已归档，业务库未修改。

09:26:50–09:46:02（Asia/Shanghai）原完整 install 和独立 fresh-JVM ArchUnit 实际均 exit0，共 5504 项：5491 通过、13 原条件跳过、0 失败、0 错误。1479 + 2 份新鲜 XML 与实际执行类对应；7 模块编译与安装类、源码前后摘要和最终 JAR 一致。Workflow 28、Skill 原生 101 和 pgvector 路由 7 项实际全部执行；租约单用例真实耗时 304.137 秒，PG 测试源码与原 full8 相同，原 1000ms / 10k 容量预算没有改变。原跳过项包括可选生产仿真、LSP / 真实模型 smoke、另行配置的记忆数据库和原独立业务窗证据，均不记为通过。

09:47:39 通过原 idle admission 后正常部署精确 JAR `4e274318f599f552738e8f74a2be421061f1bfe4ca1aadf9f058352ee2c34962`，镜像 `110aa1be1e21a13d9e51b370f121f74dd80f2cf60e91f10bb67311eebe337a2f`；Web 继续使用原受测 `13bf1248…`。11 个原运行命名空间同伴的镜像与挂载保持。部署后 13 项只读核对全部通过，原 18 业务元组逐字相同：原 A2 仅一次落地、两独立审批与 fixture-5，Source22 rev2 的唯一验收与四份完整回执，旧恢复审查状态和 OBSERVER 零消息 / 零 Run。取消权限仍为未登录401、外项目403、伪造actor403；server / browser / proxy 的660000 / 680000 / 690000毫秒契约保持。

新版本的真实模型取消、三阶段、Workflow 与 Skill 的 computer use 闭环仍待单独模型窗口实际核验，不由组件或部署结果代替。当前优先准备正常自然语言 Workflow 使用 / 停止和持久状态 / 原生连接交叉核对；后台任务继续独立执行。新的900 + 900秒业务窗、双审批、Landing、Recovery、正常任务验收和 Skill 发布 / 复用仍须逐项执行原流程。

浏览器前置审核已通过：正常Chrome页面核对Source22观察工具旧、新快照，输入输出相同，仅说明与指纹 / 来源改变。旧06c008授权保留STALE，新8ad30bed授权正常审核为仅调查阶段可用、精确test资源只读；原参数限制保存，生产和其余10项策略全部保持。20项原生读回、全部11项策略的4项范围比较通过，SQL / 原值 / 截图保存。未创建Run、调用工具或模型，不将前置审核算作Workflow执行通过。

正常Workflow配置及工具选择器也已实际只读检查：v22发布定义及hash未变，展示两个动作的名称与MCP绑定，不展示旧缓存中的PENDING或06c008，也不会从该缓存生成schemaHash绑定。正常运行目录读取最新8ad30bed审核策略，接入配置仍保存旧remoteTools描述；两种投影分开保存，最终是否正常派发仍须实际业务运行验证。本轮没有为测试直接hydrate或清缓存。

原13项跳过已逐用例记录环境：7项Memory独立MySQL / PG测试、1项真实业务窗算术组件、3项原生LSP、1项真实模型smoke、1项生产仿真。Memory现有9表及独立PG表只读盘点已完成，原7项补验脚本已准备但尚未执行，不改原完整报告的跳过结果。

## 已发布 Workflow 的实际新失败（10:42）

正常 Chrome 新对话选择既有资源观察22 / v22，发送自然语言只读目标，实际 Run `chat-chat-session-3919204b-75e7-455e-a00d-f27457024fef-3e4f4b00`。原定义hash保持 `9d2f67f9…`，02:42:09–02:42:11 UTC 实际 FAILED。第一项 `test_read_state` 真实远端成功、完整回执hash一致；第二项 `test_verify_observation` 因 EXTENSION 未在本Run激活被 `MCP_TOOL_NOT_ENABLED` 拒绝。模型请求尝试为0，Run自然结束，尚未实际点击停止，不计为取消通过或正常 Workflow 闭环通过。

只读追踪发现适配职责缺口：绑定只读 Workflow 仅把 `readOnly=true` 传入统一执行上下文，而既有 MCP dispatcher 要求 `requireReadOnly=true` 才走原正常 schema 披露 / 激活和硬只读校验。已保留失败 SQL、79条原生记录、页面、完整回执和架构定位；尚未修改新源码或调用模型。不得把 EXTENSION 改为CORE、增加生产权限、直接注入运行激活或发布新Workflow替身绕过失败。

原观察器用SQL SHA2(JSON列文本)核对canonical manifest得到false；现已按仓库规范在内存重算排序无空白JSON，精确匹配原a249a1ca…。原失败比较保留为观察器问题，不是manifest篡改。未将完整manifest或秘密写入证据。

证据在本机主工作目录 `work/resume-20261008/orbisops/`，包含可重跑脚本、源快照、原生 SQL、日志和后续截图，不直接上传凭据或私有运行记录。

### 11:15 本地普通缺陷收尾：两条只读 Workflow 路径及统一错误契约

实际已发布 v22 使用 DIRECT 回调，路径为 Graph Direct → Progressive callback → unified MCP executor → `OpsToolExecutionService.executeTypedMcp`，不是新增的 Bound TOOL 节点。两条路径都必须把明确的只读意图传为 `requireReadOnly`，以复用现有 MCP `activate` 和 `execute` 的当前原生契约、审核、项目授权与只读硬校验。未审核、停用、失效、未授权和实际写工具仍被拒绝；不改已发布 v22 定义，不给通用 dispatcher 自动启用任意工具。

真实页面首次失败的 `MCP_TOOL_NOT_ENABLED` 原生工具记录未丢失，但 EvidenceProjection 把拒绝包变成“业务证据无效”，Finalizer 又显示通用模型连接失败。现投影保留已有 typed `ToolBlockedException`，共用 MCP classifier 仅从平台 typed 拒绝包提取白名单原因，不把任意异常文本当权限原因；Finalizer 与任务详情展示复用相同安全文案。未知 typed 工具拒绝仍显示通用工具拦截，不回显参数、提示词或错误正文。

实际红灯记录分别为 17 项（11P/4F/2E）、31 项（23P/8F）、加强用户动作提示后的原 12 项（7P/5F）；全部保留独立日志与 XML。修复后原取消职责、只读 MCP、两条 Workflow mapper、typed 工具拒绝/模型排除、任务详情与原统一执行架构共 30 类 **162P/0F/0E/0skip**。新后端源码冻结 `c8a979daaf73d079d0d6b29cc0bb101b8b62ef6939fa32e05666cbd1488b6c3c`，5015 个受测源文件。原全量与当前部署仍只证明旧 `6f56 / 4e274`；新 full2、原 Memory7 原生补验、前端对应文案、实际模型等待停止及三阶段/Skill 业务仍待执行。

### 11:25 原 full2 实际失败及兼容职责修正中

前端候选已完整通过：原发布检查 / typecheck / 全部362组件项（88文件）/ build / 原40个Chrome E2E；源码8de0de37…，44静态文件hash45b50485…，未部署。原512MiB类型检查OOM仍保留，完整重跑沿用原1536MiB上限并保持串行worker。

原后端 full2 真实普通阶段退出1，停于trigger。新鲜238项中228通过、10执行错误、0断言失败、0跳过；原15项 `OpsMcpToolProviderSecurityTest` 中10项在当前核心工具的无条件远端启用前置操作失败。后续app、Memory7、真实300秒lease与独立ArchUnit未到达；旧4e274 JAR仍运行，不部署c8a候选。所有原日志 / XML / 完整FAIL审核保留。

原因是 dispatcher 在任何明确只读调用前无条件 `activate`，未区分已经具备当前审核契约的CORE与需本Run激活的EXTENSION，掩盖原当前策略拒绝并增加无用远端披露。现正把条件激活编排放回已有MCP执行Application：原授权 / 当前策略 / 硬只读先校验；仅明确只读、authoritative extension、当前Run未激活才调用原fresh activate；重新读取当前schema再次校验后才业务dispatch。CORE / 已激活不增加inspect，普通非只读extension继续原NOT_ENABLED；不递归、不业务重试。原15项安全测试保持原文。

原Memory9表1191行及PG8行 / 表结构在失败前后逐键完全一致，18业务状态和13检查保持；这是数据保持证明，不是Memory7已执行。第一次表观察器误将整个隔离schema表数等同Memory-owned9表，FAILED_OBSERVER_SCOPE及原脚本保留；明确原9表范围后的before2 / after1数据比较通过。

原15项安全测试短RED实际5P10E；新9项生命周期独立RED1P8F。首次生命周期GREEN原15项11P4F0E，后续App测试因reactor失败未到达，报告仍FAIL。其余4项揭示legacy `readOnlyHint` 为运行目录的建议属性，而非Workflow/独立核验的硬限制；旧hint可能与authoritative当前DRY_RUN / write策略不同。直接映射为hard `requireReadOnly` 会拒绝原合法DRY_RUN并改变原包审批阻断原因。修正必须区分明确只读限制与“只尝试当前native readonly extension启用”的候选意图，由同一MCP Application当前策略决定，不能相信hint给write启用。原15安全用例仍保持原文，未部署上述候选。

### 11:40 条件激活职责收尾：原兼容语义与明确只读限制分别保留

前文将 legacy DIRECT 的 `readOnlyHint` 同样视为硬只读意图，现由原15安全用例的真实反例更正：它是工具目录的建议属性，会与当前原生DRY_RUN / 写策略不同。DIRECT compatibility 只传 `allowReadOnlyActivation` 候选标记；Bound Workflow / 明确 `executeReadOnly` 继续传硬 `requireReadOnly`。最终由已有 MCP Execution Application 先核对授权、当前原生schema / policy和硬约束，仅对当前确实只读、需本Run激活而未激活的EXTENSION复用原fresh activate；然后重新读取当前schema并再次硬只读校验。CORE和已激活不额外inspect，当前写策略不会凭hint自动启用，普通extension没有候选仍为NOT_ENABLED，原演练和审批拒绝码保持。

候选转硬只读请求只复制input并增加requireReadOnly：原完整config对象（项目 / Run、MCP / tool绑定、headers、allow/block、变更包、deadline）、actor、trustedLanding、完整args都保持；raw按既有canonical JSON从全input生成。实际兼容链用例逐字段核对，fresh降级、审批状态变化、启用失败、CORE与已激活等反例同时验证，没有递归重试或重复业务dispatch。

短回归36类 / 199项实际全部通过，0失败、0错误、0跳过；原15安全测试逐字保留，4条原MCP / 统一执行架构也纳入本轮。新源码冻结 `98983361933faeaf06a7420a3d2969b3658fd171feb19a9173b55ca7b2dbe7e6`，5016文件，9生产文件及8测试文件相对稳定full1变化。36份新鲜XML与运行区间 / 源码前后hash逐项核对通过。

这只证明聚焦范围。原full2的238项 / 10错误、两轮RED、首生命周期GREEN1的4失败全部保留，不能用旧完整测试认证新候选。原入口full3与Memory7现成隔离环境已准备，尚未执行；部署仍是6f56 / 4e274，真实模型等待停止、三阶段、Skill发布 / 复用仍未通过。前端候选仍为8de0 / 45b5 / 362组件通过 / 原40 E2E通过，尚未部署。

### 12:14 原 full3 完整验证与隔离 fixture 数据差异

原完整入口 `run-workflow-readonly-full3.py` 实际11:50:45–12:14:03自然完成：普通install和独立fresh-JVM ArchUnit均exit0，新鲜1482 + 2 XML，共5546项 / **5540通过、6条件跳过、0失败、0错误**。Memory原7项此次真实MySQL / PG全部执行并通过，不再算跳过；Workflow28、Skill101均实际通过。原300秒租约用例实际302.66秒，PG10k / 1000ms保持。新源码989833、7模块compiled / installed / boot JAR字节绑定一致，受测JAR `0296751bc434dbd6a6a8b78e827638393e30784b968e4d5c9ad77519a0025c5d`。尚未部署。

只读数据比较发现：9个Memory-owned表结构、PG8旧行、MySQL1191旧行中的1190行全部保持；唯一变化是隔离历史synthetic fixture的 `ai_ops_context_memory.id=1`。原Memory7固定PROJECT/A/content投影稳定记忆ID，随后ON DUPLICATE UPDATE会以本次独立UUID来源更新同一历史fixture行；当前来源的3类任务均COMPLETED，稳定ID与原fixture内容hash精确匹配。原711旧outbox任务行逐键hash相同，因此本轮没有观察到全库replay改变旧任务；175 MySQL和2 PG新行来自实际测试。原逐字不变检查的FAIL保留，未回滚或覆盖旧状态；由于before只存整行hash，不声称事后知道每个旧字段的值。

原18业务元组与13项权限 / 数据检查仍全部通过，原A2、Source22及恢复失败均未改。候选正常部署暂由主窗口HOLD，等待对上述测试fixture来源更新的边界判断；它不影响完整测试PASS事实，也不能把数据保持FAIL改成PASS。新自然语言Workflow模型等待停止、完整三阶段与Skill发布 / 复用仍未测，本轮没有新模型运行或Git操作。
