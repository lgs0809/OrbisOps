# MCP 发现计数与恢复契约

本批对应 OPS-01/02，保留现有框架和业务数量阈值。没有将 O200K_BASE 估算改称真实主模型 tokenizer，也没有迁移 Spring AI 2.0。

## 计数范围和身份

在项目、身份及阶段授权过滤后，按完整序列化摘要计算估算值，不截取描述或用字符数替代。默认仍为业务工具不超过 20 个、摘要估算不超过 2000 时使用摘要目录；任一超限使用 Tool Search，每次至多 5 个。加载/搜索入口本身不计为业务工具，完整 Schema 仍按需加载。

当前实现是 Spring AI `JTokkitTokenCountEstimator`，默认编码 `O200K_BASE`。与特定主模型 tokenizer 的一致性没有经过认证，因此明确记录 `accuracy=ESTIMATE`。这不代表 Qwen 或部署中别名模型的精确输入 token。阈值没有改动或自动增加裕量，也不宣称最优。

新 `countingBasis` 同时记录：契约版本 `mcp-summary-count/v1`、实现类、实现与编码库公开的版本、编码、精度标记、模型配置 ID/默认模型名称和三个数量参数。库没有公开 Implementation-Version 时保存 `UNREPORTED`，不猜一个版本。模型配置身份只用于追溯，不表示编码器与该模型等价。

模式、原目录 Hash、工具数量、摘要计数和上述依据，在原 `project/run/agent/node` 唯一作用域第一次选择时一起持久化。恢复不以当前设置覆写原计数依据。搜索数量也沿原冻结值构建框架搜索器及加载限制，避免重启后从 1 扩为 5 等隐性变化。工具撤权、删除、Schema 变化仍逐轮重新检查，冻结模式不冻结旧权限。

## 迁移和旧记录

迁移 `104/ops-mcp-discovery-counting-basis` 为原选择表增加可空 JSON 列，不改已有业务行、不回填计数。可重复执行。旧行恢复时保留其模式、计数与编码，依据仍为空；运行事件以 `LEGACY_UNRECORDED` 标明，没有伪认证旧数据。

部署必须先完成正常迁移再运行新 JAR。旧 JAR 不读取新增列，因此回滚代码不需要删除该列或现有记录。迁移文件及 SHA256 在 `server/db/migrations/manifest.tsv` 登记。

## Tool Search 的实际归属

- Spring AI 1.1.4；Spring AI Alibaba 1.1.2.2 的图循环负责工具执行、检查点和取消。
- 社区 `org.springaicommunity:tool-search-tool:1.0.1` 的 `ToolSearchToolCallAdvisor` 与 `tool-searcher-regex:1.0.1` 提供发现钩子/词法索引。
- `OpsMcpToolSearch` 只适配发现钩子，不调用 Advisor 的嵌套工具执行循环。首轮仅搜索入口；搜索成功后的下一轮才提供真实 Callback。
- Schema 变化替换 Callback；名称/描述变化重建会话索引；撤权剔除。搜索词按短字面关键词处理，不执行模型提供的正则程序。

真实框架契约测试已经覆盖这些行为及 20/21 工具、计数临界、中英文完整描述、1000/10000 定义、跨 Run/Server 隔离和恢复后的搜索数量。它们使用真实框架组件和合成任务模型，不替代实际模型下的任务召回/参数正确率评测。

## 同批发现的 Skill 统计补差

使用事实通过现有事务/CAS 纠正后，聚合原先遗漏 `success` 与 `evidenceSufficient` 的增减。现与其他后置事实一同按 before→after 更新；重复事实不增加分母，反向纠正可恢复计数。更新失败与使用事实一起回滚。没有改变成功来源、自动发布或回退门槛，不重写历史统计。

## 重跑与只读检查

```sh
python3 scripts/test-mcp-counting-and-feedback.py \
  --java-home /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  --output /tmp/ops-mcp-counting-new.log
python3 scripts/test-backend-namespace-lifecycle.py
```

脚本复用原本机 MySQL 13362，创建/沿用独立 `orbisops_mcp_counting_20261102` 测试库；不改展示或原验收业务库，不调用模型、不发布 Skill。19 项聚焦包括 13 项实际框架与 6 项实际 MySQL 检查。测试事实保留，重复执行使用新 Run/项目身份；日志应选择新文件名。

SQL 保存在 `deploy/acceptance/mcp-counting-feedback-inspection.sql`，包含冻结依据及使用记录/聚合对照。完整部署门禁、实际产物与页面结果见[本批验收记录](../acceptance/2026-11-02-mcp-counting/验收记录.md)；本说明不把未测的真实模型、三阶段或组级调查复用写为通过。
