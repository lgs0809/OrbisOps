# MCP 回包、恢复与真实派发预算

## 运行契约

SDK 边界保留 `content`、`structuredContent`、`isError`、`_meta`，再增加本地的
`orbisopsResultVersion`、`normalizedContent` 与 `outputSchemaHash`。有结构化结果时优先使用；
否则连接文本块，并按工具的输出 Schema 验证。未声明结构化输出的工具允许合法纯文本。
空结果、非法 JSON、Schema 不匹配与超过 1 MiB 的结果不能作为成功证据。

工具错误、协议错误、契约错误、授权拒绝与连接故障分别记录。只读的一次逻辑调用最多派发两次，
连接重建、契约错误与瞬时故障共享这一次重试，间隔 500 ms。SDK 传输层拒绝额外派发。
会话 404 和 stdio 退出精确失效对应连接，旧 handle 拒绝使用，新连接必须重新初始化。
业务错误、参数错误和授权拒绝不累加依赖熔断器。

普通单次请求等待不超过 15 秒；已批准写请求的回执等待不超过 60 秒，均受任务剩余期限约束。
等待连接锁、初始化和预算存储也有期限。实际派发后的写结果未知时保留 UNKNOWN，不能盲重试。
只有目标自己实现 execution key 与可查询回执时，才能验证该目标的写幂等；不能推广到任意 MCP 工具。
本实现没有宣称支持工具目录变更推送通知，目录刷新后发现的 Schema 变化仍须重新审核。

## 工作流预算

在已发布图的 START 节点配置 `maxRealToolCalls`（整数 1–1000）。未配置时兼容原有图，不施加新增预算。
预算来自运行冻结的定义，调用参数不能覆盖；发布新版本也不能重置已经启动的运行。

迁移 079 增加 `ai_ops_workflow_tool_dispatch`。每次 `tools/call` 在发送前通过 MySQL 行锁与事务取得
不可退还的额度，记录 run、逻辑调用、物理尝试、RPC ID、工具和冻结定义 Hash。重试同样计费，
并发节点、服务重启和审批恢复共同使用该账本。预算耗尽、任务取消、定义不符或存储不可用时拒绝派发。
如果额度提交后进程退出或任务到期，额度仍保守占用，不能仅凭客户端没有收到响应就退还；
因此审计时分别核对“已预留额度”和远端实际接收的 RPC，不能把两者无条件视为相等。

## 隔离环境与重跑

```sh
python3 scripts/local-acceptance.py init
python3 scripts/local-acceptance.py up
python3 scripts/seed-local-acceptance.py
python3 scripts/seed-mcp-acceptance.py
```

UI 为 `http://127.0.0.1:3302`，API 为 `http://127.0.0.1:18089`。
MCP 测试进程与后端共享网络命名空间，以 loopback 提供服务；SQLite 请求与回执保存在独立持久卷。
所有端口只绑定本机。模型调用关闭，没有更换已约定模型，也不代表真实模型验收。

首次 MCP 导入会进入正常的待审核状态。打开项目 A 的工具策略页面，核对
`OPS-03 acceptance MCP / probe` 的实际 Schema 和测试进程源码，再通过页面审批只读策略。
重新执行种子脚本后才启用项目绑定并正常校验、发布只读图。脚本不直接写审批或成功状态，
重复执行复用匹配版本，遇到已修改的实体保留原值并报错。

```sh
# 仓库根目录：真实 MySQL、并发额度和冻结定义
python3 scripts/test-workflow-integration.py --tests WorkflowToolBudgetMySqlTest
# 真实 MySQL 的恢复、取消、输出保存与丢回执回归
python3 scripts/test-workflow-integration.py
# 已部署后端 + MCP 进程：生成独立测试运行、实际审批和容器重启
python3 scripts/test-mcp-runtime.py --output /absolute/path/to/mcp-runtime-result.json
# server 目录：完整回归，含 HTTP / stdio 协议矩阵
mvn -B -pl orbisops-app -am test
```

部署测试只重启 `orbisops-acceptance-backend-1` 与 `orbisops-acceptance-mcp-acceptance-1`，保留数据卷。
每次使用独立标识新建合成协议工作流和请求记录，旧失败运行保留。对预期失败场景，测试 PASS 表示
失败被正确阻断，工作流本身仍为 FAILED。

`probe` 的 count 来自测试进程实际接收并写入 SQLite 的请求数；它是协议证据，不能当成服务健康指标。
`append_record` 只写该测试进程的合成回执表。真正的业务指标、日志、数据库调查图和
PREPARE → 审批 → LANDING → 完成后验收在 OPS-04/08 单独验收。
