package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class ObservabilityToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "observability";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(
                definitions.toolset(
                        "observability.prometheus",
                        "Prometheus 查询",
                        "查询指标、QPS、错误率和资源趋势",
                        "LOCAL_PROMETHEUS",
                        true,
                        List.of(
                                tools.read("prometheus_instant_query", "查询 Prometheus 即时指标", "LOCAL_PROMETHEUS"),
                                tools.read("prometheus_range_query", "查询 Prometheus 区间指标", "LOCAL_PROMETHEUS"),
                                tools.read("prometheus_label_values", "查询 Prometheus 标签值", "LOCAL_PROMETHEUS"),
                                tools.read("prometheus_series_query", "查询 Prometheus series", "LOCAL_PROMETHEUS"))),
                definitions.toolset(
                        "observability.logs",
                        "日志检索",
                        "查询 Elasticsearch 日志",
                        "LOCAL_ELASTICSEARCH",
                        true,
                        List.of(
                                tools.read("elk_search", "检索日志", "LOCAL_ELASTICSEARCH"),
                                tools.read("elk_aggregate_errors", "聚合错误日志", "LOCAL_ELASTICSEARCH"),
                                tools.read("elk_trace_lookup", "按 traceId 查日志", "LOCAL_ELASTICSEARCH"),
                                tools.read("elk_log_context", "查询日志上下文", "LOCAL_ELASTICSEARCH"))),
                definitions.toolset(
                        "observability.traces",
                        "链路追踪",
                        "查询 trace/span 明细",
                        "MCP",
                        true,
                        List.of(tools.read("trace_search", "检索链路"))),
                definitions.toolset(
                        "local.logs",
                        "本地日志",
                        "读取白名单内本地日志",
                        "LOCAL_LOG",
                        true,
                        List.of(
                                tools.read("tail_log", "读取日志尾部", "LOCAL_LOG"),
                                tools.read("grep_log", "搜索日志", "LOCAL_LOG"),
                                tools.read("parse_error_topn", "提取错误样本", "LOCAL_LOG"))),
                definitions.toolset(
                        "tool_result",
                        "工具结果读取",
                        "读取、搜索和切片大工具输出",
                        "NATIVE",
                        true,
                        List.of(
                                tools.read("tool_result_read", "读取工具结果", "NATIVE"),
                                tools.read("tool_result_grep", "搜索工具结果", "NATIVE"),
                                tools.read("tool_result_slice", "切片工具结果", "NATIVE"))));
    }
}
