# 2026-10-01 产品与真实 Workflow 使用增量

本批是 OPS-02／04／06／08 的交互与实际使用增量，不代表全部OrbisOps完成。证据目录：本次Codex工作区下的`work/product-ux-20261001`；本地地址 http://127.0.0.1:3302 。保留原工作区改动、历史运行、不可变版本及数据库，模型仍为gpt-5.6-luna／terra。

## 实际改动

参考 [n8n执行记录](https://github.com/n8n-io/n8n-docs/blob/main/docs/build/understand-workflows/understand-executions/view-all-executions.md)：把“创建／编辑工作流”与“使用已发布工作流”入口区分，版本和运行结果清楚可见。

- `web/src/pages/agent-list.tsx`、`features/agents/components/WorkflowManualTestButton.tsx`：发布／草稿／停用筛选、搜索重置页码、无匹配、真实加载错误重试。已发布卡直接“使用工作流”，按当前身份、项目及不可变发布版本建立会话，不自动执行。编辑器测试入口保留。
- `web/src/pages/chat.tsx`、`features/chat/chat-presentation.ts`：消息预览用unified／remarkParse解析为短文本，保留用户自定义标题；Enter／Shift+Enter和中文IME边界；用户阅读历史时不强制滚动。手机隐藏列表不接受焦点／点击；项目／工作流并排、模型单独整行，顶部运行入口不被裁切。
- `features/chat/use-chat-draft.ts`：本页按账号、项目、会话保存独立未发送草稿；切换后恢复，发送成功正常清除，无永久写入。
- `features/chat/merge-chat-history.ts`：历史成功／失败任务保持“查看运行”入口，不将其再次提交；未完成任务的恢复逻辑沿用既有规则。
- `web/src/pages/workbench.tsx`：按任务内容和运行编号搜索已加载记录；提示最多100条，保留独立授权的更早链接详情；错误保留旧记录并明确可重试；列表请求用代次避免迟到响应覆盖。
- `scripts/web_asset_retention.py`、`test_web_asset_retention.py`、`deploy-tested-web-acceptance.py`：当前＋前两版静态资源，完整字节／SHA核对，旧index不保留，不无限累积。与SemEvoSQL使用相同有界保留实现。
- `web/src/pages/skill-evolver-management.tsx`、`features/skill-evolver/SkillMethodChange.tsx`：学习任务／沉淀结果／版本维护使用Semi UI页签；点击记录打开响应式SideSheet，关闭可回原列表。复用授权项目上下文，用项目名称筛选；明确状态筛选只针对任务、每类最多100条；手机页码按已加载数据派生，过滤重置。修复新格式steps／stopConditions／required／interpretationRules／rules不显示的问题，保留旧字段；禁止事项按原规则语义展示。目标Skill复用实时发布投影。
- 桌面学习结果使用Semi Typography三行摘要／完整提示，删除与首列重复的决策列；完整正文仍在详情。`BackgroundFailureNotice.tsx`与`background-failure.ts`把失败原因和真实排队状态分开，兼容只有已知lastError枚举的旧记录；FAILED不再承诺自动重试，不显示过期nextRunAt，不输出未知provider异常原文。没有修改重试业务规则或任务状态。
- 对应测试：`chat-presentation.test.ts`、`use-chat-draft.test.tsx`、`merge-chat-history.test.ts`、`WorkflowManualTestButton.test.tsx`、`web/src/pages/workbench.test.tsx`。

## 真正跑通的工作流

原隔离验收Workflow `workflow-mttgh3yz` v2是start→DIRECT agent→end，直接解析JSON。通过新卡片创建v2会话，正常中文输入后Run `chat-chat-session-f13ae05c-8863-4ffd-9739-cde50e50cd75-af0c472b`失败，INVALID_JSON_AT_POSITION:0，0次模型／MCP。失败记录保留，不归因网络。

通过真实浏览器编辑器把这个无自动化／渠道引用的只读Workflow改为框架既有REACT模式，要求读取真实target_version工具定义、从授权资源映射用户服务名，必要时自然语言询问，不要求用户JSON。正常保存、校验发布后为v4，旧v2/v3快照保留，发布hash `270d61c407e5cb9f945c6cc50049d538d8abbf37e6e8030e61c03472e5f12650`。没有修改审批、Landing权限或普通DIRECT契约。

浏览器新建固定v4对话，输入“请只读查询隔离验收项目 A 中验收服务 A1 当前实际运行的版本，给出本次查询依据，不做变更。”

- session `chat-session-ed03219b-3d8b-47e2-9f4d-946a6d304e07`。
- Run `chat-chat-session-ed03219b-3d8b-47e2-9f4d-946a6d304e07-e747e5cd` SUCCEEDED。
- 实际gpt-5.6-terra调用3次，0重试；真实target_version MCP调用1次，实际资源ops-acc-a-service-1的版本fixture-1。
- queryId `5b7f097d-6f9e-480b-9851-128fb47448b3`；回执 `tool-result-dc6844c0…`，完整值在证据JSON。`ops-workflow-v4-progress.json`包含真实模型身份、provider receipt SHA、实际资源和答案核验。
- `ops-workflow-frozen-binding.json/.sql`6项通过：项目、工作流、会话、运行、不可变版本和hash一致。`ops-workflow-published-versions.json`保留正常API读取的完整发布版本快照。

## 浏览器与自动验证

- `ops-web-verify-18.log`：typecheck、hygiene、69文件225项前端测试、build通过。不是累计多批相加。
- `ops-web-deploy-14.json`：运行前端与当前＋保留资源全字节一致，Nginx代理正常，backendRestarted=false；最新镜像与字节manifest见该JSON。
- `ops-asset-retention-unit-2.log`4项通过：版本上限、相同名称冲突、篡改和路径越界。
- 实际已发布筛选1条、草稿0条，第二页后搜索返回正确第一页8条；浏览器创建／发布／真实模型和MCP使用已经执行。
- 实际390×844，整页scrollWidth390；项目／工作流／模型选择器右界≤359，查看运行按钮右界372。A→B→A未发送草稿恢复正确，测试期间不提交查询。修复后旧失败会话可打开准确失败Run详情，再“继续对话”回原会话；成功会话切换后保留查看运行入口。`ops-mobile-history-final-proof.json`留存。
- 工作台搜索 `af0c472b` 实际显示1/100，正确失败详情继续保留；截图`ops-workbench-search.png`。
- 主要截图：`ops-workflow-published.png`、`ops-workflow-after.png`、`ops-chat-mobile-final-2.png`、`ops-draft-restored.png`、`ops-history-run-navigation.png`。`ops-workflow-query-result.png`为早期加载中截图，不能作为成功证据；后续verified和final截图替代它。
- `ops-skill-method-presentation-proof.json`实际390×844、整页390、SideSheet左0右390；6步诊断、2条停止条件、4条必须证据、4条禁止事项逐字与该次真实保存提案一致。关闭详情回原页签；项目名称筛选后版本维护显示该项目真实整组回滚，维护空状态准确。原始审计默认折叠，已发布目标不再显示待创建。手机截图`ops-skill-drawer-mobile-start.png`、`ops-skill-drawer-mobile-rules.png`及`ops-skill-maintenance-filtered.png`。
- `ops-skill-ui-db-crosscheck.json`8项PASS：实际所选学习任务、已验收来源、方法及Hash、当前ACTIVE Skill第1版、运行版本／Hash、真实READY1024维索引一致；其他角色403。调用只读`inspect-saved-skill-experience.py`／`inspect-auto-skill-publication.py`保留MySQL与PostgreSQL SQL。此批查看已有真实沉淀记录，不宣称新增SPLIT闭环。
- `ops-skill-status-filter-proof.json/.sql`3项PASS：实际选择失败状态后显示1条，与正常认证API／MySQL同一job身份一致。控件复用Semi支持的aria-labelledby，真实可访问名称为学习任务状态／筛选项目。手机初始读取显示加载状态，零条数据不渲染1/0页码。
- `ops-skill-terminal-failure-crosscheck.json/.sql`6项PASS：浏览器所选FAILED任务的job身份、错误、9次尝试和旧nextRunAt与认证API／MySQL一致；真实可见的原因与终态说明准确，未显示等待自动重试或旧重试时间。截图`ops-skill-terminal-failure-final.png`。这是已有真实失败记录的只读核验，不是新注入模型故障，也没有重置它的状态。
- `ops-skill-final-list-density-2.json`3项PASS：实际桌面1280×720、documentWidth1280；6列移除重复决策，前三条可见记录115／98／98px。初次`ops-skill-final-list-density.json`选择显式role=row没匹配Semi原生tr，不作为密度通过证据，后续按实际可见DOM行核验。
- 真实旧标签页在不刷新情况下加载工作流懒加载页面成功，`ops-old-tab-navigation-proof.json`比较主bundle未变化，成功截图保留；之后刷新载入新沉淀工作区。
- `ops-web-verify-8.log`因新增文档硬编码开发机路径被hygiene拒绝，已改为可移植路径；`-9`拒绝不存在Card description属性，已修复；`-10`拒绝页面新增effect，改为纯派生页码；后续`-11`至最新`-18`完整通过，不放宽已有检查。最终截图为`ops-skill-workspace-final-3.png`。
- 本批只更换前端；既有后端5238通过／27跳过的包保持运行。本批没有重新执行全套后端，不能把它说成本批新回归。

## 手动使用与重跑

本机既有账号从正常登录使用，不在文档复制凭据。进入 http://127.0.0.1:3302/workflows?projectId=ops-acceptance-a ，搜索“OPS-04 浏览器创建”，选择已发布 → 使用工作流。直接输入上面的普通中文。结果是该次实际目标版本查询，不能替代完整发布验收。

先将`ORBISOPS_ROOT`设为本机OrbisOps仓库路径。

```sh
cd "$ORBISOPS_ROOT"
UX_OUT="$(mktemp -d /tmp/orbisops-ux.XXXXXX)"
(cd web && npm run verify > "$UX_OUT/web.log" 2>&1)
python3 -m unittest discover -s scripts -p test_web_asset_retention.py
python3 scripts/deploy-tested-web-acceptance.py --test-log "$UX_OUT/web.log" --output "$UX_OUT/web-deploy.json"
```

已有Skill的只读重跑入口：`python3 scripts/inspect-saved-skill-experience.py --job skill-evo-d11c4005c5e0c94c9d7bf07d070f5929 --output "$UX_OUT/experience.json"`；`python3 scripts/inspect-auto-skill-publication.py --project ops-acceptance-a --output "$UX_OUT/publication.json"`。

实际数据检查SQL在`ops-workflow-frozen-binding.sql`和`ops-workflow-v4-progress-inspect.sql`等证据文件；连接13362的隔离MySQL。它们只读，不改审批或成功状态。

## 未测与剩余

本批未重新走全部三阶段、审批和任务验收；这些已有批次结果需独立引用。SPLIT6个合法来源／20个行为场景、240个独立题目和560次真实评测、完整故障矩阵、重构／Git时间线仍未完成；惠多拼未在本批实施。OrbisOps旧标签页实际跨新版本导航已通过；断网／超出三版保留窗口等场景尚未注入。手机主要入口、沉淀详情及项目范围维护入口通过，其他管理页面手机验收未全覆盖。

## 受阻

本批页面／只读Workflow／已有Skill详情验收没有新增凭据或组件阻塞。全量评测、剩余功能和其他管理页覆盖属于未完成，不包装为受阻或通过。原始失败任务仍是FAILED，修复的是展示语义。

## 同日后续增量

后续实际完成独立MCP协议矩阵、基础模式动作摘要、对话可读工具回执、按项目／工作流的异步加载、可视化创建／发布新的只读版本工作流及真实自然语言查询。详见[沉淀工作区与后续真实MCP验收](2026-10-01-learning-workspace.md)，其外部验收包保存真实模型失败、共享网络故障、修复及后续成功。本文“真实SPLIT6个合法来源／20个行为场景”沿用了已撤回的逐Skill评测要求；当前方案仅保留SPLIT至少6条独立来源、每分支至少3条及最低检查，不再加逐Skill20例发布关卡。平台全量评测仍需完成，真实SPLIT未在本批补齐。
