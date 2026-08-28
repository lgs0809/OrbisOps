# OPS-05/07：可恢复的真实经验提取

来源不变的长证据曾在每次后台重试时从头补读，耗尽原 60 秒期限后丢失导航进度。本批增加独立的阅读进度端口与 MySQL 适配器，只保存冻结来源的已验证引用 ID，不保存新的业务成功结论。恢复时从相同来源重新构建并校验原文，来源 hash、指令、模型和协议版本共同决定缓存键；权限、400000 字符请求和五轮补读预算不变。

进度通过独立短事务持久化，即使后续模型超时也保留。原生 20 路并发探针复现了 INSERT IGNORE 后锁升级死锁；改用先取得排他锁的 upsert，再锁行合并引用。错误来源和非法引用会拒绝且无部分写入。

完整门禁 5637 项＝5602 通过、35 条件跳过、0 失败/错误，包含实际五分钟租约恢复及 30 项原生架构检查。已部署 JAR `e4f5bb1490741b65de8aaf8a3f0e6a59b2f6299efb3d791685d4d012aa8dd535`；实际部署包再次通过 7 项原生进度测试，原 MCP peer 镜像和挂载保持。

实际已验收任务 `task-acceptance-ea11b21b-4e8c-4057-8ef7-6ce91c3e518b` 经正常后台执行：第 11 次处理把已读引用 10→14；第 12 次恢复这 14 条原文，一个真实 Terra 请求得出证据充分并保存方法。归组传输失败时方法仍保留；第 15 次完成归组。方法 hash、来源、实际 PG/Qwen 1024 维向量与原生分组身份等 13 项核验通过；页面方法正文可读，3 类未授权账户不能访问管理详情。

该任务最终为 SKIPPED，业务含义是“经验已保存，等待更多独立成功任务”。本轮只有一个独立来源，不用 15 次处理凑三来源，也未伪造新 Skill。CREATE/PATCH、实际复用、拆分与合并需后续独立任务认证，不能由本案例代替。

为本机验收缩短等待，只对该隔离 PENDING 任务提前五次 next_run_at，保留次数、租约和来源，后续均由真实 worker 决定结果；每次前后快照和带保护 SQL 已保存。这是测试排期控制，不是生产退避规则，也不直接写验收、经验、审批或成功状态。

另外补跑独立数据库中的工作流 31 项与记忆 13 项均通过，无跳过。记忆测试原固定 2 秒重试假设已失效，改为从实际持久期限验证“期限前不执行、到期恢复”；原失败报告保留。此次只有测试文件改变，生产源码、全部编译类及已部署 JAR 字节一致。

```sh
python3 scripts/test-workflow-integration.py --tests all --java-home "$JDK21"
python3 scripts/test-memory-integration.py --tests all --java-home "$JDK21"
python3 scripts/inspect-saved-skill-experience.py --job "$JOB" --output "$OUT/method.json"
```

使用新的绝对输出目录保留报告。两个集成脚本默认创建独立回归 schema，保留原记录且不重置业务数据库。测试报告：`skill-read-progress-full-2-reviewed`、`skill-read-progress-native-4`、`skill-v9-final-proof-1`、`skill-v9-method-native-2`、`ops-workflow-native-1`、`ops-memory-native-2`；同前缀 SQL、XML、脱敏日志及 CUA 截图在本机验收材料中保留。

全量门禁的 35 条件跳过维持原记录，不把额外 31+13 项结果改写为原全量全部通过。正式容量、完整 Episode/记忆并发矩阵、240 任务评估仍需独立验收。
