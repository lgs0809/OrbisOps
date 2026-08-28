# OPS-04／08：已有C闭环的重新核验

这是对9月26日真实闭环的只读核对，没有重新发布、创建新验收或新增独立经验来源。先前把C正向验收列为未完成的汇总不准确，旧fixture-2方案的失败不能代表后续fixture-3方案未完成。

变更 `cp-fbe8a91c-7f94-4d64-aa7a-ead00a1e35ab` 当前仍为LANDED：原v1批准哈希、两名独立审批人、一次Landing及一次真实配置写入保持一致。模拟生产实际A2仍为fixture-3／HEALTHY。

真实中文C运行 `chat-chat-session-4182a7fe-53c3-46f3-b281-8fdabfac1271-0ba4c077` 保留冻结工作流v5、原生终态检查点和三份完整MCP回执。发布前11:28:48～11:43:48 UTC、发布后11:43:49～11:58:49 UTC，各完整900秒；实际Prometheus下界801／466，独立数据库唯一请求806／468，错误均0，原版本fixture-2／fixture-3。下界与数据库请求总数采用各自定义，不能互相替换。

原Episode `task-episode-d587c695-3532-4d79-9cad-e9431c4e8d17` v2仍引用正常验收 `task-acceptance-d2235b30-a725-4586-aa23-2fee201cc67c`，11项检查、SUCCEEDED、内容Hash与来源Run一致。此次13项重新核验全部通过，证据 `ops-retained-c-{landing,task-acceptance,business-verified}-1` 及浏览器截图；原始完整远端记录在 `work/ops-mcp-compat-20260926/c-natural-followup-final.json`。

```sh
# PROOF指向交付的本机验收目录；OUT使用新的输出目录。
python3 scripts/inspect-task-acceptance.py --episode-id task-episode-d587c695-3532-4d79-9cad-e9431c4e8d17 --output "$OUT/task.json"
python3 scripts/inspect-a2-landing-history.py --package-id cp-fbe8a91c-7f94-4d64-aa7a-ead00a1e35ab --output "$OUT/landing.json"
python3 scripts/verify-retained-change-acceptance.py --evidence "$PROOF/../ops-mcp-compat-20260926/c-natural-followup-final.json" --acceptance "$OUT/task.json" --landing "$OUT/landing.json" --output "$OUT/verified.json"
```

三份输出旁均保存只读SQL。当前资源以后如果由正常变更升级，版本检查应如实失败；历史已接受窗口依然保留，不为让重验通过而回滚资源。

本次还修复辅助流量脚本的中断：原ThreadPoolExecutor退出等待会让已收到SIGINT的主线程仍等待全部工作线程。现在使用标准`threading.Event`统一停止、可中断等待和有界HTTP超时，提前停止保存`stopped=true/complete=false`，不宣称完整窗口。实际本地HTTP测试13.1秒正常退出，前一次探索流量退出143记录保留。脚本修改不改变已部署后端或业务验收判定。
