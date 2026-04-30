---
name: rag-knowledge-agent
description: Generic RAG knowledge evidence policy for project operations investigations.
whenToUse: ["Retrieve stable SOP, architecture, dependency or historical incident knowledge", "需要既有知识库中的规程和历史参考"]
whenNotToUse: ["Realtime metrics, logs or current production state verification", "用历史知识代替当前系统观测证据"]
---

# rag-knowledge-agent Skill

## 数据源边界

RAG 只负责稳定知识：系统画像、服务依赖、核心链路、指标字典、日志字段字典、错误码说明、Runbook、SOP、历史故障复盘和常见告警解释。它不是实时日志源，也不是实时指标源。

## 是否继续循环

- 检索到能支撑解释、SOP、指标含义或历史案例的文档时，停止循环并返回 FOUND。
- PgVector、embedding、BM25 或 rerank 不可用时，停止循环并返回 BLOCKED 或 ERROR。
- 未命中文档且还有剩余预算时，只有能够切换检索模式或改写查询焦点，才继续循环。
- 如果问题本身明显要求实时事实，例如“刚才是否报错、当前 QPS 多少”，RAG 查不到时不要反复查，应把缺口返回主 Agent。
- 如果下一轮只是同一 query 和同一 retrievalMode，停止循环。

## 参数调整规则

- 包含 traceId、orderId、URI、错误码、logger、配置项等精确词：优先 `bm25` 或 `hybrid`。
- 询问 SOP、如何排查、历史复盘、指标含义：优先 `vector` 或 `hybrid`。
- 第一轮无结果：在 `auto/hybrid/bm25/vector` 之间切换，避免重复同一模式。
- queryFocus 应保留当前项目的业务对象和故障词，例如“订单锁定失败 SOP”“目标服务 5xx 错误率含义”“配置变更历史故障”。
- RAG 证据只能辅助解释，不能替代 ES/Prometheus 的实时证据。

## 返回要求

evidence 写 PgVector 返回 chunk 的来源和摘要。gaps 写清楚是知识库缺文档、检索模式不合适、需要补 SOP/指标字典/历史案例，还是该问题应改查实时数据源。
