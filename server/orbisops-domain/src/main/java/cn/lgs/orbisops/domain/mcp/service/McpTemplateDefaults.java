package cn.lgs.orbisops.domain.mcp.service;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;

import java.util.List;
import java.util.Map;

public final class McpTemplateDefaults {

    private McpTemplateDefaults() {
    }

    public static List<McpTemplateDefinition> values() {
        return List.of(
                template("mysql-readonly-template", "MySQL 只读诊断模板", "mysql",
                        List.of("SHOW_SCHEMA", "SELECT", "EXPLAIN", "AGGREGATE", "JOIN"), "LOW", true,
                        "用于 MySQL 表结构、索引、慢 SQL 和只读查询诊断。项目工具必须绑定具体数据连接和可见对象。"),
                template("postgresql-readonly-template", "PostgreSQL 只读诊断模板", "postgresql",
                        List.of("SHOW_SCHEMA", "SELECT", "EXPLAIN", "AGGREGATE", "JOIN"), "LOW", true,
                        "用于 PostgreSQL schema、索引、pg_stat 和只读查询诊断。"),
                template("redis-readonly-template", "Redis 只读诊断模板", "redis",
                        List.of("INFO", "SCAN_PATTERN", "GET", "TTL", "MEMORY_USAGE", "SLOWLOG"), "LOW", true,
                        "用于 Redis key pattern、TTL、内存和慢命令诊断。"),
                template("elasticsearch-readonly-template", "Elasticsearch 日志检索模板", "elasticsearch",
                        List.of("READ_MAPPING", "SEARCH_INDEX", "AGGREGATION", "READ_LOG_DETAIL"), "LOW", true,
                        "用于 ELK 索引、mapping、日志检索和聚合分析。"),
                template("prometheus-readonly-template", "Prometheus 指标查询模板", "prometheus",
                        List.of("QUERY_INSTANT", "QUERY_RANGE", "READ_TARGETS", "READ_METADATA"), "LOW", true,
                        "用于 Prometheus 指标、标签、target 和趋势查询。"),
                template("rabbitmq-readonly-template", "RabbitMQ 只读诊断模板", "rabbitmq",
                        List.of("READ_OVERVIEW", "READ_QUEUE", "READ_CONSUMER"), "LOW", true,
                        "用于 RabbitMQ vhost、queue、consumer 和积压状态诊断。"),
                template("grafana-readonly-template", "Grafana 看板与告警模板", "grafana",
                        List.of("READ_DASHBOARD", "READ_ALERT_RULE", "READ_DATASOURCE"), "LOW", true,
                        "用于读取 Grafana 看板、告警规则和数据源引用，不执行写操作。"),
                template("kubernetes-readonly-template", "Kubernetes 只读与 dry-run 模板", "kubernetes",
                        List.of("READ_WORKLOAD", "READ_EVENT", "READ_LOG", "DRY_RUN_APPLY"), "MEDIUM", true,
                        "用于读取 workload、event、日志和执行 apply dry-run；真实变更必须进入 ChangePackage 审批。"),
                template("nacos-readonly-template", "Nacos 配置读取模板", "nacos",
                        List.of("READ_CONFIG", "READ_HISTORY", "DIFF_CONFIG"), "LOW", true,
                        "用于读取和比对 Nacos 配置；写配置必须进入 ChangePackage 审批。"),
                template("jenkins-readonly-template", "Jenkins CI 查询模板", "jenkins",
                        List.of("READ_JOB", "READ_BUILD", "READ_LOG", "DRY_RUN_PIPELINE"), "LOW", true,
                        "用于读取 job/build/log 或执行 pipeline dry-run；触发真实发布必须进入 ChangePackage 审批。"),
                template("gitlab-ci-readonly-template", "GitLab CI 查询模板", "gitlab_ci",
                        List.of("READ_PIPELINE", "READ_JOB", "READ_LOG", "DRY_RUN_PIPELINE"), "LOW", true,
                        "用于读取 pipeline/job/log 或执行 pipeline dry-run。"),
                template("cmdb-readonly-template", "CMDB 资产查询模板", "cmdb",
                        List.of("READ_SERVICE", "READ_DEPENDENCY", "READ_OWNER"), "LOW", true,
                        "用于读取服务、依赖、负责人和资源归属。"),
                template("http-api-readonly-template", "HTTP API 只读模板", "http_api",
                        List.of("GET", "HEAD", "OPTIONS", "DRY_RUN"), "MEDIUM", true,
                        "用于受控内部 API 只读或 dry-run 调用。"),
                template("webhook-dryrun-template", "Webhook dry-run 模板", "webhook",
                        List.of("READ_SUBSCRIPTION", "DRY_RUN_NOTIFY"), "MEDIUM", true,
                        "用于读取订阅和通知 dry-run；真实外发通知必须进入审批边界。"),
                template("custom-bridge-template", "自定义 Bridge/MCP 模板", "custom",
                        List.of("READ", "VALIDATE", "DRY_RUN"), "HIGH", false,
                        "用于通过 Bridge/MCP 契约扩展新组件。默认不能业务调用，需管理员审核 Tool Policy 后启用。"));
    }

    private static McpTemplateDefinition template(
            String id,
            String name,
            String resourceType,
            List<String> actions,
            String riskLevel,
            boolean readOnly,
            String description) {
        return new McpTemplateDefinition(
                id,
                name,
                resourceType,
                "stdio",
                Map.of("serverTemplate", resourceType + "-policy-mcp"),
                actions,
                riskLevel,
                readOnly,
                description,
                McpTemplateStatus.ENABLED,
                "system");
    }
}
