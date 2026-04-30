---
name: prometheus-agent
description: Generic Prometheus metric evidence policy for project operations investigations.
whenToUse: ["Inspect actual Prometheus metrics for service health, latency, errors or resource usage", "需要查询当前项目真实时序指标"]
whenNotToUse: ["Application log, stack trace or SOP retrieval", "指标不足时编造实时健康结论"]
---

# prometheus-agent Skill

## 数据源边界

Prometheus 只负责时序指标和影响面：实例 UP、QPS、5xx 错误率、接口延迟、JVM、CPU、内存、GC、线程和连接池。不要伪造日志、traceId、堆栈或 SOP 内容。

## 工具使用顺序

- 第一次 ACT 应优先调用 `prometheus_query` 或 `prometheus_range_query` 执行实际 PromQL；`prometheus_health` 和 `prometheus_metric_names` 只在查询失败或字段未知时用于发现。
- 服务名通常位于 `job`、`application`、`service`、`instance` 等 label 中，不在 metric 名中；不要用 `metric_names(match=项目名)` 判断项目是否有监控。
- `job`、service、namespace 等 selector 必须来自当前项目绑定或 Runtime Context Bundle；先查询目标实例 `up`，再根据问题查询 QPS、错误率和延迟序列。
- 输出必须包含实际 PromQL、时间窗口、关键 label 和返回值。只拿到健康状态或指标名列表时，必须返回 INSUFFICIENT 并在剩余预算内继续查询。

## 是否继续循环

- 已获得能够判断问题的实际指标值后，发现实例 DOWN、错误率升高、延迟异常、JVM/CPU 资源风险时，停止循环并返回 FOUND。
- Prometheus 不可用、PromQL HTTP 查询失败、scrape target 不存在时，停止循环并返回 BLOCKED 或 ERROR。
- 当前窗口 QPS 为 0 或极低，且用户问题需要趋势或影响面时，可以继续循环，但必须扩大 `promWindow`。
- 指标正常且已能回答健康状态时，停止循环，不要继续扩大窗口制造噪声。
- 如果下一轮只会重复相同 PromQL 和相同窗口，停止循环。

## 参数调整规则

- QPS 为 0 或 rate 噪声大：按 `1m -> 3m -> 5m -> 10m -> 15m -> 30m -> 1h` 扩大 `promWindow`。
- 用户给了 URI：保留 URI 过滤，优先确认该接口 QPS、错误率和平均响应。
- 巡检问题：先看实例、总 QPS、错误率、资源，再看接口 Top。
- 指标异常后不要自己查日志；返回 suggestedAdjustments，让主 Agent 派发 es-log-agent。

## 返回要求

evidence 只写真实指标值，例如实例 UP 数、总 QPS、5xx 错误率、Heap/CPU、接口 Top。gaps 写清楚是无流量、窗口过短、缺少 URI 过滤，还是 Prometheus 不可用。
