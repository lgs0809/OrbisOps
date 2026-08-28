# 后台模型持续退避

本契约适用于 Episode 分段与整理、记忆抽取与摘要补做、Skill 独立安全与归因复核、Skill 演化中的模型技术失败。后台工作继续使用原有 MySQL 队列、租约和 Spring 定时维护，不影响正常用户对话。

## 等待、身份与幂等

- 后台累计领取次数不再作为永久停止上限。Episode、记忆和独立复核从 30 秒起，Skill 演化从 60 秒起，指数增长，本地等待最多 1 小时；使用按任务身份和累计次数确定的向上抖动，多个失败任务不会集中唤醒。
- Provider 的 Retry-After 只会延长等待。恢复也不缩短旧任务已持久化的 cooldown。Skill proposal 仍保留原有至少 15 分钟的等待。
- `attempts`/`attempt_count` 是累计领取与调度记录，不代表成功次数，也不等于底层每次 HTTP 请求的数量。服务重启、显式恢复和旧状态迁移不会清零它们。
- 错误、冻结输入、来源身份、下一次时间写入现有队列表。Worker 崩溃后先持久化冷却，再允许新租约领取；旧 token/epoch 不能提交投影或收费重复写工具。
- 初次摘要和摘要补做都先在事务外请求模型，再由完成事务核验租约并执行本地摘要 CAS、历史版本和补做记录。模型调用不占据完成事务。

前台 Run 的时限、单次 HTTP 的有限重试、Workflow 重试预算、取消和生产写审批规则保持各自原契约。持续退避不能用来无限重复执行工具写操作。

## 有效结果与失效来源

合法的空抽取、`UNKNOWN` 安全/归因结论、`NO_CHANGE`、无可复用模式等业务结果正常完成或跳过，不为取得更好的答案反复重试。没有复用模式的同一已接受来源不再由 grouping backfill 每分钟复活。

冻结输入损坏、固定格式/范围不合法、已撤销的来源或已失效的 Review 进入隔离/失效状态。Review 在领取和模型调用前均检查当前发布仍为 ACTIVE/CANARY、当前输入及原始用户的现有权限。这里的 CANARY 是旧存储状态兼容；ACTIVE 发布仍按现有独立复核方案处理，不重新引入旧灰度流程。

记忆模型失败时规则结果可先使用，但来源仍明确为规则。抽取保留 `RULE_FALLBACK_MODEL_PENDING`；摘要记录 `context_compressor_model_pending`，并在同一个既有 outbox 新建 `COMPRESSION_MODEL`，冻结当时原始来源。模型补做成功才产生 `llm_context_compressor` 版本。来源已清除或被更新摘要取代时不会复活旧内容。

## 向量记忆与词法兜底

SEMANTIC 区分配置意图和临时就绪状态。明确关闭或没有有效向量配置时，正常写入 `LEXICAL` 并完成，不新建补做，也不扫描旧完成记录。已启用且配置有效、但 embedding 或向量写暂时失败时，词法仍可用，原 SEMANTIC 任务保留 `LEXICAL_MODEL_PENDING`，以原次数和下次时间持续退避。

原消息的会话、项目、作者、顺序、内容及有效/替代状态构成冻结来源 hash。模型调用前核验原来源、原用户权限及当前租约。继续使用 Spring AI `VectorStore`，向量物理行按任务和 epoch 隔离，词法沿用稳定来源 ID；两者在检索融合时归并为同一来源。

PostgreSQL 写入本身不会激活新向量。检索对新标记行检查 MySQL 原任务已完成 VECTOR、epoch 相同、原来源仍保留且 hash/状态相同、当前查询者及原用户仍有现有权限。取消、清理、修改源或迟到旧 Worker 的物理行即使仍留在 PG，也不可见。这里是读路径的激活守卫，**不宣称 MySQL 与 PostgreSQL 存在原子事务**；阶段失败行可以保留供审计。

旧的没有新投影标记的记录继续原兼容逻辑，不称为通过新守卫认证。旧 FAILED SEMANTIC 如果从未记录来源 hash，不在后台猜测并恢复；需要先通过真实来源身份校验处理。当前审计的 2230 条历史消息对应三类处理记录：每类 2230、共 6690 条，全部已完成；本机制不会回填它们或批量触发付费模型。

## 旧停止状态的渐进恢复

迁移 105 仅添加摘要补做载荷列和恢复审计表，不改写历史结果。

正常后台维护每 30 秒轮转一种队列，每次最多恢复一个旧任务；各类候选分批扫描，不一次唤醒全部历史失败。恢复前必须同时满足：

1. 原项目和会话身份仍有效，能够找到原用户；原用户仍启用，并按现有项目访问规则拥有权限。合法 USER 私有记忆不额外要求项目成员；历史 NULL/空项目均按原 owner 和会话范围核验。
2. 当前对应功能、模型和必要配置仍可用；关闭功能不会被恢复器开启。
3. 原输入 hash、当前已接受来源和必要发布状态仍有效。

旧失败原因、累计次数和任务身份保留，恢复动作另写 `ai_ops_background_model_recovery_audit`。无法确定原用户的旧记录不会以管理员身份替代执行；这类记录需要先修复真实身份映射，不能称为已自动恢复。

此处保持原有历史读取契约：`project.enabled=0` 不自动等同撤权或取消后置模型任务。本批没有新增“暂停项目后停止后台”的业务规则；账号停用、明确撤权、取消及删除/变更来源按上述守卫阻断。

安全上下文拥有原用户策略，恢复及存储适配器通过 `BackgroundTaskOwnerAccessPort` 使用它；该策略通过窄 `BackgroundProjectAccessPort` 复用原项目授权。不会在 Episode 服务中直接跨上下文调用项目用例，也不会在 Project 上下文读取 Security 领域状态。

管理员手测可使用同一受保护入口：

```text
POST /api/v1/admin/ops/background-model-retries/{family}/{taskId}/recover?projectId={projectId}
```

`family` 为 `EPISODE_TURN`、`EPISODE_CONSOLIDATION`、`MEMORY`、`SKILL_REVIEW` 或 `SKILL_EVOLUTION`。即使管理员调用，也继续核验原用户和配置。旧 `MANUAL_RETRY` 入队旗标不再绕过恢复守卫。返回是否排队后应读回实际任务状态，不能把接收请求当作模型完成。

当前本机验收环境的记忆 LLM 抽取与压缩开关保持关闭。历史规则完成记录不回填、不批量触发模型，不能将它们统计为真实模型成功。

## 本地复验

真实 MySQL 目标测试复用现有验收数据库容器并使用独立 schema，保留合成测试事实；不会启动模型、改展示业务库或制造审批成功：

```bash
python3 scripts/test-mcp-counting-and-feedback.py \
  --database orbisops_mcp_counting_background_20261103 --migrate \
  --tests BackgroundModelMemoryMySqlIT \
  --java-home /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  --output /absolute/path/to/a-new-test-log.log
```

输出文件必须是新路径，避免覆盖旧证据。只读查看使用 `deploy/acceptance/background-model-retry-inspection.sql`。完整门禁与真实部署结果由相应日期的验收记录单独记录，旧 JAR 的成功证据不适用于新源码。
