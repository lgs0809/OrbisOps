package cn.lgs.orbisops.application.resourcehealth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Application query service aggregating all operational resource probes. */
public class ResourceHealthApplicationService {

    private final MySqlResourceHealthProbePort mySqlProbe;
    private final PgVectorResourceHealthProbePort pgVectorProbe;
    private final ElasticsearchResourceHealthProbePort elasticsearchProbe;
    private final PrometheusResourceHealthProbePort prometheusProbe;
    private final ModelResourceHealthProbePort modelProbe;
    private final McpRegistryResourceHealthProbePort mcpProbe;
    private final ChannelResourceHealthProbePort channelProbe;
    private final ResourceHealthSettings settings;
    private final Clock clock;

    public ResourceHealthApplicationService(
            MySqlResourceHealthProbePort mySqlProbe,
            PgVectorResourceHealthProbePort pgVectorProbe,
            ElasticsearchResourceHealthProbePort elasticsearchProbe,
            PrometheusResourceHealthProbePort prometheusProbe,
            ModelResourceHealthProbePort modelProbe,
            McpRegistryResourceHealthProbePort mcpProbe,
            ChannelResourceHealthProbePort channelProbe,
            ResourceHealthSettings settings,
            Clock clock) {
        if (mySqlProbe == null
                || pgVectorProbe == null
                || elasticsearchProbe == null
                || prometheusProbe == null
                || modelProbe == null
                || mcpProbe == null
                || channelProbe == null
                || settings == null) {
            throw new IllegalArgumentException(
                    "RESOURCE_HEALTH_DEPENDENCY_REQUIRED");
        }
        this.mySqlProbe = mySqlProbe;
        this.pgVectorProbe = pgVectorProbe;
        this.elasticsearchProbe = elasticsearchProbe;
        this.prometheusProbe = prometheusProbe;
        this.modelProbe = modelProbe;
        this.mcpProbe = mcpProbe;
        this.channelProbe = channelProbe;
        this.settings = settings;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public ResourceHealthSnapshot snapshot() {
        List<ResourceHealthCheck> checks = List.of(
                safe(mySqlProbe::probe, "mysql_slow_sql", "MySQL 慢 SQL", "jdbc:mysql"),
                safe(() -> pgVectorProbe.probe(settings.ragVectorTableName()),
                        "pgvector", "PgVector 知识库", settings.ragVectorTableName()),
                safe(() -> elasticsearchProbe.probe(
                                settings.elasticsearchUrl(),
                                settings.elasticsearchIndex()),
                        "elasticsearch", "Elasticsearch 日志", settings.elasticsearchUrl()),
                safe(() -> prometheusProbe.probe(
                                settings.prometheusUrl(),
                                settings.prometheusJob(),
                                settings.prometheusInstance()),
                        "prometheus", "Prometheus 指标", settings.prometheusUrl()),
                safe(modelProbe::probe, "model", "OpenAI/兼容模型", "spring.ai.openai"),
                safe(mcpProbe::probe, "mcp", "MCP 配置库", "ai_client_tool_mcp"),
                safe(channelProbe::probe, "channel", "消息 Channel", "ai_ops_channel"));
        long healthyCount = checks.stream()
                .filter(ResourceHealthCheck::healthy)
                .count();
        return new ResourceHealthSnapshot(
                LocalDateTime.now(clock).toString(),
                checks,
                healthyCount,
                checks.size() - healthyCount);
    }

    public List<ResourceCapability> capabilities() {
        return List.of(
                new ResourceCapability(
                        "rag",
                        "PgVector 运维知识库",
                        "VECTOR_BM25_RRF_RERANK",
                        "知识、手册、历史工单、图文结构化描述"),
                new ResourceCapability(
                        "elasticsearch",
                        "Elasticsearch 日志",
                        "DSL_QUERY",
                        "应用日志、异常堆栈、调用链关键字段"),
                new ResourceCapability(
                        "prometheus",
                        "Prometheus 指标",
                        "PROMQL",
                        "实例存活、QPS、错误率、JVM、接口耗时"),
                new ResourceCapability(
                        "mysql_slow_sql",
                        "MySQL 慢 SQL",
                        "SQL",
                        "slow_log/performance_schema 中的慢查询与执行摘要"),
                new ResourceCapability(
                        "mcp",
                        "MCP 工具库",
                        "PROGRESSIVE_DISCLOSURE",
                        "项目绑定工具按目录发现、按需启用，并由平台 Tool Policy 决定是否可执行"),
                new ResourceCapability(
                        "channel",
                        "消息 Channel",
                        "INBOUND_AND_OUTBOUND",
                        "外部消息进入 Agent 会话，以及告警、巡检和分析结果的统一通知"));
    }

    private ResourceHealthCheck safe(
            ProbeAction action,
            String id,
            String name,
            String endpoint) {
        try {
            ResourceHealthCheck check = action.probe();
            return check == null
                    ? ResourceHealthCheck.unavailable(
                            id,
                            name,
                            endpoint,
                            "资源探针未返回结果")
                    : check;
        } catch (RuntimeException error) {
            String message = error.getMessage();
            return ResourceHealthCheck.unavailable(
                    id,
                    name,
                    endpoint,
                    message == null || message.isBlank()
                            ? error.getClass().getSimpleName()
                            : message);
        }
    }

    @FunctionalInterface
    private interface ProbeAction {

        ResourceHealthCheck probe();
    }
}
