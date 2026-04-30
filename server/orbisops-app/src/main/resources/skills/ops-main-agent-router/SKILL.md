---
name: ops-main-agent-router
description: Helps the operations main agent dynamically generate datasource sub-agents and dispatch tasks with evidence budgets.
whenToUse: ["Plan evidence-driven operations investigation across available project data sources", "需要按问题选择数据源并组织有预算和停止条件的排查"]
whenNotToUse: ["Granting authority or delegating production mutations", "已有明确单一原子动作而无需组织调查"]
---

# ops-main-agent-router Skill

## 目标

主 Agent 不应该把子 Agent 写死为固定数据源集合，而是根据“可用数据源目录”和用户问题，动态生成本轮需要执行的子 Agent 任务。

## 子 Agent 生成原则

- 先识别问题需要的是实时事实、历史知识、指标趋势、日志样本还是数据库证据。
- 只给有信息增益的数据源生成任务；不确定的数据源放入 `conditionalTasks`，不要默认立即执行。
- 任务必须包含 `source`、`agent`、`goal`、`reason`、`priority`、`condition`。
- 同优先级且互不依赖的数据源可以并行，例如 Prometheus 和 Elasticsearch。
- 依赖前置证据的数据源放到条件任务，例如“Prometheus 发现延迟异常后查 MySQL 慢 SQL”。
- 不生成恢复、调控、变更或写入类子 Agent。需要处理动作时，只生成“建议/验证/人工确认”任务。

## 数据源选择

- RAG：SOP、架构说明、指标/日志字典、历史故障、Runbook。用于解释和处理方案，不作为实时证据。
- Elasticsearch：应用日志、traceId、orderId、URI、logger、level、错误码、异常堆栈。用于定位请求事实。
- Prometheus：实例 UP、QPS、错误率、延迟、JVM、CPU、内存、GC。用于判断影响面和趋势。
- MySQL 慢 SQL：慢查询、高扫描行数、高执行次数、SQL 摘要。用于定位数据库瓶颈。

## 派发规则

- 用户只问 SOP/原因/历史经验/数据源边界/安全策略，且没有当前/最近/线上验证诉求：只派发 RAG。
- 用户给 traceId/orderId/错误码/异常堆栈：先派发 Elasticsearch。
- 用户问 QPS/错误率/延迟/资源/健康：先派发 Prometheus。
- 用户问 MySQL 慢 SQL/慢查询/索引失效/数据库慢/`rows_sent`/`rows_examined`/`query_time`/SQL digest/performance_schema：先派发 MySQL 慢 SQL，不能用 RAG 替代数据库事实源。
- 用户说“接口慢并报错”：并行派发 Prometheus 和 Elasticsearch；如果证据指向数据库，再派发 MySQL 慢 SQL。
- 用户问题为空：先派发 Prometheus 做轻量巡检，其他数据源按证据触发。

## 停止规则

- 子 Agent 返回 FOUND 且证据足以支撑结论时，停止继续派发同类数据源。
- 子 Agent 返回 NOT_FOUND/INSUFFICIENT 时，只有存在明确参数调整或跨数据源信息增益时才继续。
- 子 Agent 返回 BLOCKED/ERROR 时，不要反复查询该数据源，记录原因并转向可用数据源或结束。
- 达到 `maxRounds` 或 `max-task-executions` 后必须停止，报告剩余证据缺口。
