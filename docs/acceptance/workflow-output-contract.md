# 持久化节点出口契约

默认模式仍是 `SHADOW / DURABLE_ONLY`。隔离验收覆盖文件为 `deploy/compose.acceptance.yml`，将需要恢复保证的运行设为 `GUARDED`。本次没有把所有短图改为持久化图，也没有新增节点自动重试。

节点可在 `config.outputContract` 声明 `format: TEXT` 或 `format: JSON`。JSON 必须提供 `schema`；可选 `rule` 使用现有有界 Workflow Rule AST / 表达式编译器。输出验证发生在成功检查点之前，恢复回放时再次执行。没有声明契约的旧节点保留原输出格式，但仍检查空回包、错误信封、大小、输出归属、冻结定义和资源授权。

```json
{
  "outputContract": {
    "format": "JSON",
    "schema": {
      "type": "object",
      "required": ["result", "sampleCount"],
      "properties": {
        "result": {"enum": ["HEALTHY", "UNHEALTHY", "INSUFFICIENT_DATA"]},
        "sampleCount": {"type": "integer", "minimum": 0}
      }
    },
    "rule": "nodeOutput.result != 'HEALTHY' || nodeOutput.sampleCount >= 100"
  }
}
```

上例仅展示契约机制，不是完整巡检规则。真实业务图还必须执行清单中的窗口、SLO、基线、证据和预算判断；声明某个 JSON 值不会使它成为真实观测证据。完整合成反例见 `scripts/test-workflow-runtime.py`。

- `output` 存在时作为业务数据；否则校验节点结果对象。JSON 字符串必须完整解析，不接受尾随垃圾。TEXT 必须是非空文本。单节点结果上限 1 MiB，不截断后伪装成功。
- Schema 复用已有 MCP JSON Schema validator；禁止远程 Schema 引用。规则只读取受限 Map 根，不支持执行 Java、SpEL 或对象方法。
- 若输出声明 runId、sessionId、projectId、nodeId、agentId、agentVersion、definitionHash、planHash，必须与可信运行一致。未声明身份的旧输出不被迫改变格式。
- 节点执行前、出口和回放均检查源节点及定义未漂移、绑定运行与上下文一致；复用编译期资源校验重新查询项目授权。实际工具仍经过既有运行时权限、审批包和 execution key 门禁。
- HUMAN_APPROVAL 输出须与该运行、该节点的持久审批记录一致。审批 ID 由页面确认时冻结，不能用旧节点的 ID 审批新节点。
- `nodeValidation:<node>:<attempt>` 与输出、哈希写入同一成功检查点；旧 worker、取消或过期租约不能提交。恢复按真实完成的 attempt 回放，跳过没有成功输出的等待 attempt。

重跑：先 `python3 scripts/local-acceptance.py up`，再执行 `python3 scripts/test-workflow-integration.py` 和 `python3 scripts/test-workflow-runtime.py`。后者会重启隔离后端；所有发布、审批与取消均通过正常 API，SQL 仅查询证据。
