# OPS-06／07：沉淀工作区状态与发布版本

本批修复实际页面的状态观察，不代表真实SPLIT或整份清单完成。证据保存在外部本机验收包 `closeout-20261001`，具体地址见交付链接，不在公共仓库记录个人主目录。

## 改动

- `web/src/features/skill-evolver/evolution-record.ts`：单方法发布使用当前发布投影的 releasedVersion；旧 appliedVersion 仅在没有投影时使用。0、负数、非整数、字符串或缺失不冒充版本；回滚显示该次原发布版本，不冒充当前头。原子包单列整组状态，不编造所有资产共用的版本。
- `web/src/features/skill-evolver/api/skill-evolver-queries.ts`：复用React Query。可见工作区每10秒读取既有任务和发布记录，隐藏页面不轮询；进行中详情每5秒同步，终态停止详情轮询。只读请求不创建学习任务，不触发模型。
- `web/src/pages/skill-evolver-management.tsx`：展示上次真实同步时间，桌面／手机均显示本次发布版本；打开详情后按同jobId和projectId更新当前结果，使用既有来源匹配保护，不借用同Run的其他任务。
- 回归：`evolution-record.test.ts`、`api/skill-evolver-queries.test.tsx`。核验真实发布版本优先级、历史回滚、原子包、多种非法值、进行中→终态轮询停止及后台窗口不轮询。

## 通过

- `ops-evolver-web-2.log`：release hygiene、TypeScript、229项测试、构建通过。`ops-evolver-web-deploy-1.json/.log`：43个当前静态文件与实际容器字节一致，有界旧资源保留；后端没有重启。
- 真实浏览器沉淀结果第一条为 `evolved-7dd5530f5b2df037`，已生效v1；同时历史合并为已回滚，策略拒绝为未生效，不能都显示完成。`ops-publication-version-desktop-1.png`。
- `ops-learning-publication-1.json/.sql`4项通过：真实API投影的目标、ACTIVE与releasedVersion1，均等于MySQL的同candidate发布事实。此项是当前发布一致性，不能替代此前真实任务、来源提取、模型作者、发布及独立新任务使用的证明；完整链路见2026-09-28-discovery验收记录的2026-10-01增量。
- `ops-workspace-auto-sync-1.json`：没有点击刷新，真实页面上次同步从21:19变为21:28。只证明正常同步发生，不用时钟变化假称学习任务完成。
- 实际手机390×844打开第一条已发布方法详情，完整适用条件、停止条件、证据标准可阅读，原始审计字段默认折叠。`ops-learning-mobile-geometry-1.json`：documentWidth390、抽屉left0／right390／width390。`ops-learning-detail-mobile-390.png`。结束恢复默认尺寸。

## 失败与剩余

`ops-evolver-web-1.log`在TypeScript阶段失败（新增测试mock缺info、合并详情类型需明确）；未进入回归，修复后-2完整通过。保留历史模型失败、原方法被回滚后拒绝的旧提案，不删除记录或把拒绝改成成功。

`ops-grouping-baseline-1.json/.sql/.pg.sql`只观察到18个真实已验收事实、5个私有方法组，Hash一致、无待投影；状态OBSERVED不等于SPLIT或全部语义质量通过。现有作者仅使用当前方法组的完整成功来源。跨职责拆分的候选输入范围仍需继续核对，不能把不同组的任意来源拼入普通创建／更新，不能通过重复运行或手动改库凑分支数量。

真实SPLIT、240个独立问题及560次平台评测、最终完整故障矩阵与重构／Git日期整理尚未完成。本批没有新增凭据或依赖阻塞，不把未测归成已通过。

## 重跑

访问 http://127.0.0.1:3302/settings/advanced/skill-evolver ，正常既有本机管理员登录后打开沉淀结果或版本维护。全部后台动作与生产Landing审批仍为原边界。

```sh
cd "$(git rev-parse --show-toplevel)"
LEARN_OUT="$(mktemp -d /tmp/orbisops-learning.XXXXXX)"
(cd web && npm run verify > "$LEARN_OUT/web.log" 2>&1)
python3 scripts/deploy-tested-web-acceptance.py --test-log "$LEARN_OUT/web.log" --output "$LEARN_OUT/web-deploy.json"
python3 scripts/inspect-skill-experience-grouping.py --output "$LEARN_OUT/grouping.json"
python3 scripts/inspect-skill-publication-acceptance.py --help
```

仅前端部署没有重启后台或删除数据卷；所有证据采用新文件名，不覆盖旧失败和旧截图。

## 同日增量：真实 MCP、可视化编辑与可读回执

- OPS-03：新增独立 `OPS-03 protocol closeout` 只读协议夹具，不重新启用原先已停用的集成。`seed-mcp-acceptance.py --mcp-name ... --approve-read-only` 通过正常导入、发现与管理员策略批准，只批准 probe 为 LOW／只读，禁止生产 Landing；种子第二次导入复用同一工作流 v1。
- `test-mcp-runtime.py` 保存实际 SELECT，按指定 MCP 身份核对策略，启动请求等待期限为240秒；发布／审批均调用正常 API。`ops-mcp-protocol-full-3.json/.sql`16项通过，包括结构化、多文本、一次重连、空回包、共享重试预算、12次物理预算、业务错误不熔断、三条真实重叠并发及重启后同审批／同预算恢复。SQLite 业务服务与业务回执保持不变。这是合成协议测试，不等于真实业务或模型质量评测。
- `WorkflowDirectActions.tsx`：基础模式显示动作顺序和工具名称，参数编辑留在高级模式；DIRECT 隐藏无效的节点 Prompt，测试页显示实际节点／连接。无需用户在聊天填写参数。`ops-workflow-editor-web-3.log`232项通过。
- `AssistantAnswer.tsx`／`workflow-tool-answer.ts`／`chat-presentation.ts`：识别完整的既有 MCP 回执，将实际字段和值显示为数据卡片，原始审计折叠；不把0、false、null丢失，不把接口调用完成写成业务验收通过。普通模型 Markdown保持原样，截断的会话预览使用中性的回执提示；大回包有界展示，工具文字不执行 HTML 或加载外部资源。两处对话页面复用同一组件。
- `workflow-editor-query.ts`／`workflow-builder.tsx`：复用 React Query 的项目与工作流键。并行读取定义及项目能力，加载／失败时不展示默认草稿；拒绝跨项目定义，迟到的旧项目响应不覆盖当前编辑器。保存／校验／发布后取消旧读取并刷新当前项目缓存，不用窗口聚焦覆盖未保存编辑。
- `ops-workflow-loading-web-2.log`：hygiene、TypeScript、72文件242项测试与构建通过；`ops-workflow-loading-web-deploy-1.json`确认当前43文件与容器字节一致。此前工具卡片237／238项均通过，首轮加载修复因遗漏 Paragraph 类型引用未进入测试，修复后242项全过。
- 实际浏览器从已发布v1点击手动执行，发送正常中文“请执行当前工作流的只读核对，保留实际工具回执。”。`ops-workflow-browser-execution-1.json/.sql`8项通过：固定发布版本／Hash、原始消息、绑定路由、1次真实远端只读调用、不可变回执Hash、实际返回字段及聊天持久化一致。此夹具v1未配置 START 物理预算，预算恢复另由完整协议矩阵验证。远端关联采用精确参数＋运行时间窗，不冒称持久化RPC ID直接关联。
- 真实浏览器展开／折叠原始回执，并检查390×844手机：页面宽390、数据卡宽317、无整页横向溢出。`ops-tool-answer-mobile-390.png`；桌面 `ops-workflow-browser-completed-1.png` 的首次图仍有截断JSON预览，预览修复后的截图另存，不覆盖旧图。
- 实际浏览器通过可视化表单创建 `只读版本核对 · 页面验收 20261001`，选 REACT 及当前已授权观测MCP，保存、校验并发布v1。`ops-workflow-created-published-1.json/.sql`5项核对 UI来源、版本、活动指针、工具绑定及API/MySQL一致。创建／发布通过与随后业务查询结果分别记录。

### 网络恢复缺陷及修复范围

真实查询发现独立后端重启后，共享 `network_mode=service:backend` 的其他MCP仍占用旧网络，容器内部healthy不能证明后端能访问它们。`backend-namespace-lifecycle.py`按当前 Compose 标签及后端精确身份发现此前运行的共享网络服务；先暂停、在finally恢复到当前网络，核对网络namespace、原镜像和全部挂载，未运行服务不自动启动。部署器及所有后端重启验收脚本共用这条生命周期，不逐脚本维护易漏的服务列表。

`ops-peer-namespace-repair-1.json/.log`真实恢复4个原已运行MCP，后端未重启、镜像和挂载相同。`ops-namespace-lifecycle-unit-1.log`4项通过，涵盖后端变化抛错仍恢复、只选择当前后端的运行服务、镜像／挂载变化不报成功、有活跃任务时不开始中断。新查询首次因连接失败未获得版本，下一轮模型供应方失败；均保留原状态及截图，不算业务查询通过。恢复后的完整重启矩阵与新查询实际结果在外部验收包继续更新。

```sh
MCP_OUT="$(mktemp -d /tmp/orbisops-mcp.XXXXXX)"
python3 scripts/seed-mcp-acceptance.py --mcp-name 'OPS-03 protocol closeout' --approve-read-only --output "$MCP_OUT/seed.json"
python3 scripts/test-mcp-runtime.py --mcp-name 'OPS-03 protocol closeout' --output "$MCP_OUT/protocol.json"
python3 scripts/inspect-workflow-browser-execution.py --run chat-chat-session-4cc8d360-9dcf-42c2-abc7-e4c83a38e295-955d2daa --output "$MCP_OUT/browser.json"
python3 -m unittest discover -s scripts -p test_backend_namespace_lifecycle.py -v
```

完整协议命令会在没有其他活跃前台Run或Skill租约时实际重启隔离后端及此前运行的共享MCP。部署前后数据卷保留，不执行清库、不改成功／审批状态。五条其他重启脚本本批已复用生命周期并做语法检查，未把语法检查称为五条完整场景重跑。Skill真实SPLIT来源范围及全量评测仍为待完成，未改既有3／6来源规则。

恢复后第三次普通中文查询实际成功：`ops-workflow-natural-proof-1.json/.sql/.sqlite.sql`11项通过，冻结UI发布v1／Hash、真实Luna／Terra模型标识、1次成功只读target_version、不可变MCP回执、SQLite查询台账、目标实际/version及MySQL服务表均对应fixture-1。`ops-workflow-natural-completed-2.png`保留真实回答，前两次失败仍存在。AVAILABLE是查询数据可得状态，本批不据此声称服务健康或SLO达标；没有把此查询当作Skill任务验收或新学习来源。

最终手机断点证明使用`ops-tool-answer-mobile-geometry-3.json`：documentWidth390、viewportWidth390、cardWidth317，原始审计折叠，截图`ops-tool-answer-mobile-390-final-2.png`。此前-1／-2量到1280的另一个标签桌面尺寸，保留但不算手机通过。

修复后的`ops-mcp-protocol-full-4.json/.sql/.log`16项全部通过，业务服务数据与写回执不变；最后审批恢复仍是原审批ID、同总预算3、epoch2。生命周期实际恢复landing-mcp／mcp-acceptance／mcp-discovery／observability-mcp，全部网络namespace相同且原镜像／挂载保留。`ops-mcp-full-recovery-crosscheck-1.json/.sql`再次核对3条并发区间确实重叠、4个当前网络监听可达、主机实际观测查询台账可读、原停用MCP不被重启操作重新启用。首次只读复核猜错表名的检查错误单独保留，不当作产品失败或篡改成功状态。
