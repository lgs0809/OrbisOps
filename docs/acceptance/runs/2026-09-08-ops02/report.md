# OrbisOps OPS-02 实施与本地验收

日期：2026-09-08。基线 master 2749fa8；保留 OPS-01 改动，未提交或推送。全部故障注入、发布、审批和取消仅涉及隔离验收环境及明确标注的合成记录。

## 本批结论

**OPS-02 的通用持久化执行、审批恢复、输出契约、租约隔离及所列组件级故障测试通过。** Browser、API、MySQL 检查点与运行状态已经交叉核对。完整 PREPARE → 审批 → LANDING → 业务资源验收尚未通过；它依赖 OPS-04/08 的真实测试目标与模型配置，不能用本批合成审批图代替。

最终全量回归 4568 项：4548 通过、0 失败、0 错误、20 跳过。20 项中，8 项 Workflow MySQL 已在独立命令通过，7 项记忆数据库测试已在 OPS-01 单独通过；另 5 项模型/类生产 Landing/LSP 依赖测试仍未完成。各命令有重复覆盖，不能把数量直接相加。

## 已落地内容与文件

| 内容 | 核心变更 |
|---|---|
| GUARDED 配置接管 | deploy/compose.acceptance.yml 在隔离环境启用 GUARDED / DURABLE_ONLY；源码默认 SHADOW 不变，节点默认尝试上限 3 不等于自动重试 3 次 |
| 节点输出契约 | OpsWorkflowNodeOutputContract、OpsWorkflowNodeOutputBoundary、OpsTypedWorkflowExecutionCoordinator、OpsAgentWorkflowStructuralCompiler：复用 JSON Schema 与有界规则 AST，检查结构、声明身份/版本、业务不变量、当前资源授权；成功检查点之前阻断，恢复回放再次校验 |
| 租约与取消 | JdbcWorkSessionRunRepository、WorkSessionRunApplicationService：检查点/heartbeat/manifest/挂起/完成均检查租约及 epoch；取消不能被 claim 重置；无活动 worker 的等待任务通过取消命令直接收口 |
| 审批记录固定 | OpsWorkflowApprovalWebApplicationService、OpsWorkflowApprovalController、WorkflowApprovalPanel、ops-workflow-approval-service：提交审批时带确认弹窗冻结的 approvalId，拒绝旧节点或错误记录，保留实际 actor 权限检查 |
| 回放缺陷 | OpsTypedWorkflowExecutionCoordinator：遍历下一份已完成 attempt 输出，跳过没有输出的审批等待 attempt；避免把首次等待当成已完成节点；新完成输出不会在后续循环重复回放 |
| 确定性节点 | OpsRuntimeModelRequirement、OpsRuntimeModelResolver、HumanApprovalNodeCompiler、AgentNodeDefinitionPolicy：纯结构和人工审批节点不要求模型或虚构 Agent；没有替换模型选型 |
| 页面真实流程 | chat.tsx 防止切换/创建会话未完成就发送；workbench.tsx 接入审批、等待轮询、保留确认弹窗、运行选择同步 URL；workflow-builder、agent-config、ops-agent-canvas 保留人工审批类型，提供审批表单，加载完成前禁用保存发布 |
| 展示修复 | AnalysisTaskPresentationPolicy：仅在装配时隐藏不可用工具，不再被错误展示为本次运行尝试越权；实际 TOOL_CALL_BLOCKED 仍保留 |
| 可重跑入口 | scripts/seed-workflow-acceptance.py、scripts/test-workflow-integration.py、scripts/test-workflow-runtime.py、两版审批 fixture；新增/更新专项测试。精确路径见 changed-files.txt |

契约使用方法见仓库 docs/acceptance/workflow-output-contract.md。JSON/TEXT 均可显式声明；单节点上限 1 MiB；拒绝远程 Schema 引用和可执行表达式。合成健康结论规则只是机制反例，不代表已经查询真实指标。

## 实际测试命令与结果

在仓库根目录执行 Python；Maven 在 server，npm 在 web。

| 命令 | 实际结果 | 证据 |
|---|---|---|
| `mvn -B test` | types 9 + trigger 148 + app 4411 = 4568，4548 通过 / 20 跳过 | server-final.log |
| `mvn -B -pl orbisops-app -am '-Dtest=OpsTypedWorkflow*Test,OpsWorkflow*Test,AgentWorkflowCompilationPipelineTest,AnalysisTaskPresentationPolicyTest' -Dsurefire.failIfNoSpecifiedTests=false test` | 74 通过，含 20 个新出口契约/边界用例 | output-boundary-tests.log |
| `python3 scripts/test-workflow-integration.py` | 真实 MySQL + HTTP 8 项通过，无跳过 | mysql-cancel-final.log |
| `python3 scripts/test-workflow-runtime.py` | 8 个场景通过：重启/冻结版本/旧审批；等待取消；6 个出口契约反例与正例 | runtime-final.log |
| `npm run verify` | 47 文件 / 133 测试通过；类型检查、仓库发布卫生、生产构建通过 | web-editor-final.log |
| `python3 scripts/local-acceptance.py up` | 前后端镜像构建、既有 077 迁移校验、依赖及后端健康检查通过；本批未新增数据库迁移 | deploy-contracts.log、deploy-cancel-response.log |
| 浏览器保存、校验并发布 | approval-retest 正常发布 v3，三个 HUMAN_APPROVAL 与 1800 秒有效期保留；v1 历史运行未变化 | browser-editor-published.json、截图 06/07 |
| `git diff --check`、Python AST 语法检查 | 通过 | 本地命令 |

真实数据库/组件场景：检查点 INSERT 故障完整回滚后重试；20 次取消与完成并发竞争仅一方提交；自然租约过期拒绝旧 worker 的 checkpoint/heartbeat/manifest/挂起/完成；接管后旧 epoch 仍被拒绝；两个并行节点的输出在持久状态汇合且恢复后均存在；未知写进入 REVIEW_REQUIRED；真实 HTTP 服务已提交数据库写后断开响应，第二次 execution key 预留被拒绝，实际写入计数仍为 1。

并行汇合覆盖真实 durable coordinator 与 MySQL；尚未发布带业务 fan-out 的运维图。丢响应覆盖真实 HTTP/数据库/幂等账本；尚未据此宣称所有 MCP 工具或外部资源支持 execution key。故障触发器仅建在独立回归 Schema，测试后移除，未通过改库制造审批或成功状态。

## Browser / 数据库交叉证据

1. 浏览器发起 v1 两审批节点任务：`chat-chat-session-cf00aa44-7f89-437d-91eb-74d87f5a5714-2e977569`。
2. 等待第一审批期间通过正常 API 发布含第三节点的 v2，并实际重启隔离后端。重启前后审批 ID、摘要、过期时间和待审批状态一致。
3. 在真实工作台页面确认第一项，进入独立第二项。复用第一项 ID 返回 403，第二项记录保持不变。再在页面确认第二项。
4. MySQL 最终 SUCCEEDED、epoch=3、agentVersion=1；两项审批均 APPROVED 且记录真实合成管理员 actor；未出现 v2 的第三项。所有检查点的 planHash/contextBundleHash 恒定。
5. 在真实工作流编辑器修改第一项审批说明，正常保存/校验/发布 v3；三个审批类型与时限不变，原 v1 运行仍成功。不是直接修改发布状态。

上述精确 Hash 与审批记录见 browser-final-evidence.json、retest-final.tsv。新版出口契约部署后的独立 API/重启复验见 runtime-final.log，重复验证冻结定义与恢复成功；错误业务输出任务实际为 FAILED，成功检查点数量为 0，测试断言通过不等于这些任务执行成功。

截图 01/02 为初次失败运行中的真实审批阶段；03/04/05 为修复后的浏览器复测；06/07 为最新编辑器与正常发布。没有删除旧失败记录或改造成成功。

## 失败、未测与受阻分列

| 类别 | 记录 |
|---|---|
| 通过 | 上述 8 项真实组件故障/并发测试、8 场景实际运行、浏览器两次审批与编辑发布、自动化回归 |
| 发现失败，已修复并复测 | 取消仍可能写成功、过期租约仍可提交；旧审批无记录 ID；纯审批图被要求模型/Agent；审批等待 attempt 导致第二次恢复失败；会话创建竞态；画布误标人工审批；等待取消返回 500，已改为拒绝响应 403 |
| 保留的历史失败 | 初次浏览器工作流 run 尾号 79078ee0 在两次审批后恢复失败，原始记录见 browser-run-final.tsv；旧回放缺陷修复后的独立任务成功。runtime-contracts.log 保留取消命令已生效但响应映射错误的首次自动测试失败 |
| 未测 | 三条真实业务图及其自动触发、防重、真实业务 fan-out；PREPARE → 审批 → LANDING → 完成后待验收的业务资源全链路；MCP 协议级断联矩阵；真实外部写回执对账（后续 OPS-03/04/08） |
| 受阻 | 隔离环境没有真实模型凭据且模型调用关闭；真实模型/embedding/Landing 模型闭环不计通过。全量中 OpsOpenAiCompatibilityRealSmokeTest、OpsProdLikeJavaLandingAcceptanceTest、3 个 LSP 依赖用例跳过；后续仍继续无模型可验证工作 |

## 访问与重跑

- UI： http://127.0.0.1:3302；API： http://127.0.0.1:18089。
- 项目 A/B、6+6 合成客户、12+12 关联订单、3+3 服务，以及只读数据资源沿用 OPS-01，未清空或覆盖。此处服务场景标识尚不是实际时序指标服务。
- 凭据留在仓库 deploy/.acceptance-private 和 deploy/.env.acceptance，权限 600、Git 忽略，未放进交付附件。
- 初始化：`python3 scripts/local-acceptance.py init`，`python3 scripts/local-acceptance.py up`，`python3 scripts/seed-local-acceptance.py`，`python3 scripts/seed-workflow-acceptance.py`。重复导入保留已有实体/角色/凭据；版本不匹配时拒绝覆盖。
- 自动回归：`python3 scripts/test-workflow-integration.py`，`python3 scripts/test-workflow-runtime.py`。后者正常创建新合成运行并重启隔离后端；业务 SQL 只读查询证据。审批 fixture 默认 seed 重跑复用原始 agent，`--agent-id ops-acceptance-...` 可显式建独立复验身份。
- 停止：`python3 scripts/local-acceptance.py stop` 保留全部数据卷；恢复：`python3 scripts/local-acceptance.py start`。不部署生产、不开放公网、不使用付费模型。

关键日志与 PNG 已脱敏，SHA256SUMS 可核验附件。秋招简历、导学、面经与事实账本未改动；本报告用于原窗口更新资料。
