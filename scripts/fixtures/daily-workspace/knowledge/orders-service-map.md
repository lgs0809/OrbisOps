# 交易中台服务与数据契约

本资料为本机日常工作空间的合成业务说明，不是线上事故或运行成功的证明。

订单 API 接受创建与查询请求。库存预占在创建订单时记录库存校验结果，订单查询只负责读取已存在订单。三项服务依赖同一 orders-store；共享依赖仅代表关联线索，不证明同一故障原因。

只读数据源包含 service_inventory、request_observation、service_dependency、service_configuration 与 change_window。request_observation 是固定合成历史观察，字段包含 service_id、request_key、occurred_at、response_code、duration_ms 与 outcome。相同 request_key 的两条观察是同一业务请求的重复接收，统计时应同时报告接收次数与去重请求数。

时间范围固定为 2026-09-29 至 2026-10-08。该台账不能证明此刻可用性；实时判断必须引用接入的实际指标与日志。没有实际指标时应保留“不确定”，不能从演示数据推导实时根因。

配置变更只应由完整 PREPARE 产物、独立审批和 LANDING 执行。值班角色只能调查和准备建议，不能直接修改生产数据或把任务完成等同业务恢复。
