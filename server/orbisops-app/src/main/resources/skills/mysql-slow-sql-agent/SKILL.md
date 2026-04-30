---
name: mysql-slow-sql-agent
description: MySQL slow SQL sub-agent loop policy for operations investigations.
whenToUse: ["Investigate actual MySQL slow SQL, statement digests or SELECT execution plans", "已有 SQL 问题或指标日志指向数据库耗时，需要只读核对"]
whenNotToUse: ["Database mutation, schema changes or repair execution", "没有数据库线索时把慢接口直接归因为 SQL"]
---

# mysql-slow-sql-agent Skill

## 数据源边界

MySQL 慢 SQL 子 Agent 只负责数据库侧证据：`mysql.slow_log`、`performance_schema.events_statements_summary_by_digest`、SQL 摘要、耗时、扫描行数、返回行数和执行次数。不要伪造接口日志、实例指标或 SOP 内容。

## MCP 工具使用

- 优先调用 `mysql_health` 确认 `mysql.slow_log` 和 `performance_schema.events_statements_summary_by_digest` 是否可用。
- 时间窗口内慢 SQL 明细优先调用 `query_slow_log`，参数使用主 Agent 给出的 `rangeMinutes`、阈值和样本数。
- `mysql.slow_log` 没有命中或不可用时，调用 `query_statement_digest` 查询累计高耗时 SQL；如果已知业务库，优先设置 `schemaName`，避免把 Grafana、系统库等噪声当成业务证据。
- 需要判断索引时，可以用 `show_table_indexes` 或 `explain_select` 补充元数据，但只解释 SELECT，不执行写入、变更或修复。

## 是否继续循环

- 找到超过阈值的慢 SQL、高扫描行数 SQL 或高频高耗时 SQL 时，停止循环并返回 FOUND。
- `mysql.slow_log` 和 `performance_schema` 都不可用、权限不足或查询失败时，停止循环并返回 BLOCKED。
- 当前窗口没有慢 SQL 且仍有剩余预算时，可以扩大 `rangeMinutes` 后继续，但最大不超过 240 分钟。
- 如果 slow log 没开但 performance_schema 可用，可以降级查询累计摘要；必须在 evidence 或 attempts 中说明数据源不是时间窗口内明细。
- 如果下一轮只能重复相同窗口和阈值，停止循环。

## 参数调整规则

- 无命中：优先把 `rangeMinutes` 从 15 扩到 30/60/120/240。
- 用户提到接口变慢但没有 SQL 关键词：只在 Prometheus/ES 证据指向数据库耗时后再查询。
- 发现扫描行数高：建议检查执行计划、索引选择、where 条件选择性和分页/排序。
- 发现执行次数高：建议确认是否存在热点接口、批量任务或 N+1 查询。

## 返回要求

evidence 只写真实 SQL 侧证据，例如命中数量、最高耗时、平均耗时、Top SQL、扫描行数和执行次数。gaps 写清楚是 slow log 未开启、performance_schema 不可用、时间窗口无命中，还是缺少业务接口/traceId 关联。
