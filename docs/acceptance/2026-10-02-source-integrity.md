# OPS-02／04／06／07：子任务证据、分批作者与失效发布

本记录是本地验收增量，不代表整个项目完成。原失败记录、历史提案正文及验收数据保留；业务状态来自正常执行、审批和验收服务，没有直接修改数据库制造成功。

## 已完成的实现

- `JdbcTaskEpisodeRunScope` 从已提交检查点解析同项目、同创建者的子 Run，核对固定定义及身份。任务验收、成功证据和经验读取共用该范围；子任务计入父任务证据，不独立增加学习来源。深度、数量及原回执 Hash 均有边界检查。
- `SkillSourceBatchPolicy`、`SkillAuthoringProgressPort`、`JdbcSkillSourceBatchReview` 和 `OpsSkillSourceBatchReview` 使用原后台任务、原作者模型与原提案：每页最多读取20个完整来源，关联 Skill 最多5份完整正文，分页审阅落盘后才做最终决策。迁移099保存不可变审阅台账；重放必须核对原提案、来源及租约。历史方法来源不冒充 PATCH 所需的新来源。
- `JdbcSkillLifecycleVisibility` 统一运行和作者的原子版本可见规则。作者不会将已回滚目标或已被替换原方法当成当前有效方法；工作流已有的明确绑定与历史正文读取保留原规则。
- `SkillPublicationAdmissionPolicy` 区分已明确失效的冻结基线与暂时性故障。尚未发布的 READY 提案可经现有状态协调器关闭；已写入包、未知数据库结果及网络异常继续走原对账／退避。已有失败 Job 不改为成功，旧提案正文不重写。

## 实际业务与机制验收

证据在本机交付包 `closeout-20261002`，每个探针旁保留实际 SQL。

- 浏览器普通中文触发日常巡检 A v6，异常后实际执行告警调查 B v8；等待审批时重启，随后在页面正常批准。实际观测目标产生859个请求／trace。A检查近5分钟的280个已完成请求下界；B保留窗口尚未完整的限制，不声称根因已确定或服务恢复。
- 修复前任务验收因遗漏子任务证据而失败。修复后页面正常确认17项条件，通过验收；冻结2个 Run、23份完整回执，含2条子任务消息和139条子任务轨迹。验收 Hash 与来源 Hash、实际数据库及页面一致。后台真实 Terra 提取经验后暂存，因来源数量不足未发布，不冒充新 Skill 已生效。
- 八项工作流引擎矩阵、数据绑定重启矩阵、六项子图协议重启矩阵及三项告警重启检查通过。子图协议是明确标记的合成组件夹具，不能替代真实业务图。先前使用不匹配业务窗口的子图尝试失败记录保留。
- 分页协调、21条来源、审阅台账完整性／篡改／旧租约等由自动化与真实 MySQL 集成验证。两次真实 Terra 读取合成来源时正确指出缺少实际业务证据，未完成模型分页或发布；单列为“拒绝不足证据通过”，不能称真实多页业务闭环通过。
- 失效版本专项94项通过。补调度入口后的专项70项通过，覆盖旧 READY 的明确失效原因可重新核验，而网络、PENDING_INDEX及旧灰度仍保留退避。首轮编译错误单独保留。
- 本批最终完整后端5,266通过、27跳过、无失败／错误；前端251项通过，lint／类型／hygiene及构建通过。运行 JAR SHA256 为 `aa9b9a6632f9a83772cdd0dff46127ca369a1a28a91533b22c6702907e364224`，已核对容器实际字节；前端实际静态资源与构建一致。
- 已通过原后台协调器关闭旧冻结基线失效的未发布提案。候选 `skill-candidate-6242fe80-03ca-4e67-a4bd-d96a64c5b2fe` 为 ROLLED_BACK、发布版本0；原作者任务仍 FAILED／8次，原 planHash 和 authoredHash 未变。没有把失败任务改成成功。三个无权账号均403，API与实际 MySQL 状态一致。
- 补齐失败作者任务的只读发布投影，按项目／Job／source 精确关联原提案，安全归一为 BASELINE_STALE，不暴露原内部异常正文。真实浏览器详情可同时读取保存的经验及“提案已关闭（未发布）”；原失败状态保留。专项64项及页面回归通过，先前编译失败日志保留。
- 完整测试中的27项跳过不计通过。随后用独立回归Schema补跑记忆7项、工作流11项、工具预算3项，共21项通过／无跳过；这些是实际数据库组件测试，仍有合成会话／Run夹具，不冒充真实用户业务。完整测试原JSON保持原计数，追加结果独立保存。
- 真实Code MCP的6项通过，包括原来跳过的JDT LS、Pyright及共存3项，核对符号、引用、诊断刷新和隔离工作区进程清理。实际组件旧样本的C算术策略1项通过，仅验证四类SLO判断，不冒充当前批准发布。Spring AI网关兼容性1项真实通过：请求及provider回包模型均为 `gpt-5.6-luna`，只发一次HTTP，没有转发器或SDK隐藏重试。
- 原27项跳过共26项已另行补测通过，追加测试总29项含3项先前已通过的Code MCP检查，不重复计算为新增。唯一未跑的是需预备独立产物及旧依赖端口的8092本地Java物理发布夹具；没有为跑这项测试去覆盖现有目标。探针首轮转发器不支持Spring分块请求的失败保留，未发到实际模型；改用Node原生HTTP解析后通过。
- `seed-local-acceptance.py --verify-only` 原先错误要求全库等于最初行数，在合法新增业务行后误报。现验证本种子的固定ID及项目、外键关系，额外行保留并报告。实际A全库4服务／6客户／13订单、B3／6／12，种子身份仍均3／6／12且12笔关联完整；账号项目隔离、管理接口拒绝和MySQL只读拒写通过。种子未调用模型，报告改为 NOT_RUN_BY_SEED，不再硬编码“缺凭据”。

## 重跑与手动使用

正常登录 http://127.0.0.1:3302/chat?projectId=ops-acceptance-a ，通过巡检工作流入口发送业务语言，审批位于 PREPARE 与 LANDING 之间。Skill 工作区： http://127.0.0.1:3302/settings/advanced/skill-evolver 。

```sh
OPS_PROOF="$(mktemp -d /tmp/orbisops-source-integrity.XXXXXX)"
python3 scripts/inspect-task-child-evidence.py --episode-id task-episode-fdbcd134-6ab9-471e-baa4-e96b6daf77cf --verify-accepted --output "$OPS_PROOF/child.json"
python3 scripts/inspect-skill-lifecycle-visibility.py --verify-closed --output "$OPS_PROOF/visibility.json"
python3 scripts/inspect-authored-skill-publication.py --job skill-evo-3f0f165d81e04e7f7311d00a73ac2d7a --output "$OPS_PROOF/authored-publication.json"
python3 scripts/inspect-skill-source-portfolio.py --help
python3 scripts/test-skill-source-batch-model.py --help
```

专用数据库集成重跑（本机需将Java21和Maven加入环境）：

```sh
python3 scripts/test-memory-integration.py
python3 scripts/test-workflow-integration.py --tests WorkflowMySqlTest
python3 scripts/test-workflow-integration.py --tests WorkflowToolBudgetMySqlTest
python3 scripts/seed-local-acceptance.py --verify-only
python3 scripts/test-authorized-model-compatibility.py --output "$OPS_PROOF/model-compatibility.json"
```

上述入口使用当前验收数据库容器中的独立回归Schema，运行正常迁移；不清空验收业务库，新增夹具均使用独立合成身份。

以上前两个是只读验证。模型分页探针会调用已配置的真实 Terra，但合成来源只能验证协议和拒绝行为，不构造实际验收事实。

部署先执行真实 `mvn -B -f server/pom.xml package` 和 `cd web && npm run verify`，分别把成功日志交给 `deploy-tested-acceptance.py`、`deploy-tested-web-acceptance.py`。部署器检查活跃任务，保留全部卷、四个共享 MCP 的原镜像／挂载及网络生命周期；不删除旧失败。

## 后台网络重试与无需变更的实际收尾

原任务 `skill-evo-a602f8fcb0d91be4def4d4d8bc1c555b` 曾因8次网络暂存及1次普通格式失败累计FAILED／9次，说明总尝试数错误地挤占了普通失败额度。`SkillEvolutionJobPolicy` 与 JDBC 原任务队列现在分别记录全部尝试和普通失败；迁移100保守初始化旧值，不静默复活旧FAILED任务。网络与既有暂存原因继续原指数退避，普通失败仍按既定额度终止。CAS、租约及epoch约束保留；过期租约恢复和重放同样使用独立记账。真MySQL反例覆盖连续网络暂存后连续3次格式失败，第三次才终止；每次重新创建仓储读取保留值，重复提交不重复扣额度。

在真实页面点击“重新分析这项任务”，使用原Run／Job／已验收来源，旧审计不删。实际经历一次网络暂存后自动重试；最终SKIPPED／2次尝试／普通失败0次、无活租约。真实gpt-5.6-terra读取原输入1455505字符，返回NO_CHANGE：已有方法A覆盖只读身份／版本／现状证据，人工方法B未允许自动维护，不应自动合并，当前证据不支持拆分。原planHash／authoredHash与数据库字节Hash一致，候选为空；这是有效无变更，不是模型失联或新增发布。

同期间实际通过computer use发送普通中文查询discovery-service-05。数据库记录后台及前台同时RUNNING；前台完成一次真实只读MCP调用，返回discovery-v5／HEALTHY，与SQLite记录一致，0次写调用。随后在页面中文说明验收要求，由模型整理3项字段核验，正常“确认并核验”后通过。该记录是本地合成服务状态读取，不证明真实服务修复或持续健康，也不扩充为独立平台评测题。

`SkillEvolutionDiagnosticPort.AuthoredDecision`使用原不可变提案，按项目／Job／来源精确关联，校验作者输入、输出和来源Hash后只读投影产品结论。旧patch、Job和提案正文均未重写；来源更新时明确标记非当前。页面仅在当前来源的终止NO_CHANGE中显示“无需变更”及模型原原因，不将运行中／FAILED／旧来源当成已完成。网络说明优先读取已知暂存原因，未知供应方异常正文不展示；已有提案的发布状态仍独立处理。

最新完整package：5296项中5269通过、27跳过、0失败／错误；前端257项、hygiene／类型及构建通过。后端与运行JAR SHA256均为 `14f2b97d31f4cc1aef85855724848c76d97957c68aa9b3366cb387ba7e90a384`。部署前活跃Run／Job均0，全部数据卷及四个MCP镜像／挂载保留。原27项跳过及前文另外解决26项的证据分开记，不相加凑通过数。

证据：`ops-retry-migration-preservation-verified-1.json`7项、`ops-background-recovery-and-foreground-verified-1.json`14项、`ops-authored-decision-live-1.json`9项及旁存SQL；实际截图`ops-foreground-with-background-task-acceptance-1.png`、`ops-authored-decision-browser-1.png`。首轮新MySQL测试遗漏另外两个完整来源的入队，并在异常后留下夹具任务，导致后续测试串扰；已按正常入队补齐并finally结清测试租约，第二轮专项与最新完整回归通过，原失败日志保留。这是专用合成回归夹具缺陷，不冒充线上业务失败。

只读复验（报告使用新目录）：

```sh
OPS_RECHECK="$(mktemp -d /tmp/orbisops-background.XXXXXX)"
python3 scripts/inspect-skill-retry-accounting.py --job skill-evo-a602f8fcb0d91be4def4d4d8bc1c555b --output "$OPS_RECHECK/retry.json"
python3 scripts/inspect-skill-authored-decision.py --job skill-evo-a602f8fcb0d91be4def4d4d8bc1c555b --output "$OPS_RECHECK/decision.json"
python3 scripts/inspect-task-acceptance.py --episode-id task-episode-5ea9eb66-1fe6-4519-b1d2-cf53b7437e79 --output "$OPS_RECHECK/foreground-acceptance.json"
python3 scripts/verify-skill-background-recovery.py --help
```

恢复核验器消费交付包中保存的实际前后记录、模型终态、并行观察和普通验收，不启动新的模型或把旧失败写成成功。三个无权限账号均403；API新增字段与数据库原作者结论一致。

## 尚未完成

真实业务 SPLIT、超过20个真实合格来源的模型分页／恢复闭环、240个独立问题及560次平台评测、最终故障矩阵仍未完成。本地 Git 日期整理已按确认的窗口／频率完成，原历史、原index和逐文件验证的工作区备份保留，未推送。没有新增凭据／依赖受阻。未测不计通过，来源不够不以重复 Run 凑数。
