---
name: es-log-agent
description: Generic Elasticsearch log evidence policy for project operations investigations.
whenToUse: ["Investigate actual Elasticsearch application logs within the project and time window", "需要从真实日志核对错误、链路或请求证据"]
whenNotToUse: ["Metrics-only health or throughput queries", "缺少日志数据源时编造日志或用 SOP 代替实时证据"]
---

# es-log-agent Skill

## 数据源边界

Elasticsearch 只负责真实日志证据：时间窗口、level、traceId、orderId、URI、logger、message、错误码和异常堆栈。不要用日志结果推断实例健康、QPS 趋势或 SOP 结论。

## 工具使用顺序

- 已知项目索引和字段时，第一次 ACT 应直接调用 `search`；`list_indices`、`get_mappings` 只在索引或字段查询失败时用于发现。
- 索引、service 字段和值必须来自当前项目绑定或 Runtime Context Bundle；Skill 不得写死某个示例项目的索引和服务名。
- 查询应同时保留时间窗口和用户问题中的业务线索，可组合 ERROR/WARN、5xx、错误码、traceId、orderId、URI 和异常类型。
- 命中非目标 service 的样本不能作为当前项目证据，必须按项目资源绑定收紧过滤后重查。
- 输出必须包含实际 Query DSL 摘要、命中数、目标 service 和代表性业务样本。只有索引、Mapping 或其他服务日志时必须返回 INSUFFICIENT。

## 是否继续循环

- 找到 ERROR/WARN、异常堆栈、明确错误码、traceId/orderId/URI 相关样本时，停止循环并返回 FOUND。
- ES 不可用、索引不存在、HTTP 查询失败时，停止循环并返回 BLOCKED 或 ERROR。
- 当前窗口完全无日志且仍有剩余预算时，可以继续循环，但必须扩大时间窗口。
- 有日志但没有 ERROR/WARN 时，只有用户明确问异常、报错、traceId、orderId、接口失败，或其他数据源提示异常接口时，才继续；普通健康检查可以停止并返回 INSUFFICIENT。
- 如果下一轮只能重复相同窗口和相同过滤条件，停止循环。

## 参数调整规则

- 无命中：优先把 `rangeMinutes` 从 15 扩到 30/60/120，最大不超过 240。
- 有精确条件：保留 traceId、orderId、URI、错误码，不要为了召回随意删除精确条件。
- 业务关键词过窄：保留精确条件，将业务描述降级为 should 语义。
- 需要人工复核样本时，设置 `includeRecentLogs=true`。
- 不要把 ES 当全文知识库；SOP、历史案例和指标解释应交给 rag-knowledge-agent。

## 返回要求

evidence 只写真实查询结果，例如日志总量、ERROR/WARN 数、Top logger、样本数量。gaps 写清楚是时间窗口不足、过滤条件不足、没有异常级别日志，还是数据源不可用。
