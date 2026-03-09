package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns resource-type normalization, capability defaults and permission semantics. */
final class OpsProjectMcpTypePolicy {

    String normalize(String type) {
        String value = OpsProjectMcpConfigValues.text(type, "mysql")
                .toLowerCase(Locale.ROOT)
                .replace("-", "_");
        if ("pgsql".equals(value) || "pg".equals(value)) return "postgresql";
        if ("elk".equals(value) || "es".equals(value)) return "elasticsearch";
        if ("k8s".equals(value)) return "kubernetes";
        if ("gitlab".equals(value)) return "gitlab_ci";
        if ("http".equals(value) || "api".equals(value)) return "http_api";
        return value;
    }

    List<String> allowedTools(String type) {
        return switch (normalize(type)) {
            case "mysql" -> List.of(
                    "mysql_health",
                    "query_slow_log",
                    "query_statement_digest",
                    "show_table_indexes",
                    "explain_select");
            case "postgresql" -> List.of(
                    "postgresql_health",
                    "query_pg_stat_statements",
                    "show_postgresql_indexes",
                    "explain_postgresql_select");
            case "redis" -> List.of(
                    "redis_health", "redis_info", "redis_scan", "redis_get", "redis_ttl");
            case "rabbitmq" -> List.of(
                    "rabbitmq_health",
                    "rabbitmq_list_queues",
                    "rabbitmq_queue_detail",
                    "rabbitmq_list_consumers");
            case "elasticsearch" -> List.of("list_indices", "get_mappings", "search");
            case "prometheus" -> List.of(
                    "prometheus_health",
                    "prometheus_metric_names",
                    "prometheus_query",
                    "prometheus_range_query");
            case "openapi" -> List.of("openapi_list_operations");
            case "service_control" -> List.of(
                    "get_service_status",
                    "restart_service_dry_run",
                    "restart_service",
                    "get_operation_receipt");
            default -> List.of();
        };
    }

    int timeoutSeconds(String type) {
        return switch (normalize(type)) {
            case "prometheus", "elasticsearch" -> 30;
            case "mysql", "postgresql" -> 15;
            case "redis", "rabbitmq", "openapi", "service_control" -> 10;
            default -> 20;
        };
    }

    String defaultEndpoint(String type) {
        return switch (normalize(type)) {
            case "mysql" -> "mysql://127.0.0.1:3306/app";
            case "postgresql" -> "postgresql://127.0.0.1:5432/app";
            case "redis" -> "redis://127.0.0.1:6379/0";
            case "rabbitmq" -> "http://127.0.0.1:15672";
            case "elasticsearch" -> "http://127.0.0.1:9200";
            case "prometheus" -> "http://127.0.0.1:9090";
            case "openapi" -> "http://127.0.0.1:8080/v3/api-docs";
            case "service_control" -> "service-control://service";
            default -> "local";
        };
    }

    String defaultUsername(String type) {
        return switch (normalize(type)) {
            case "mysql" -> "root";
            case "postgresql" -> "postgres";
            case "rabbitmq" -> "guest";
            default -> "";
        };
    }

    Map<String, Object> enrichPermission(
            String type,
            Map<String, Object> permission) {
        Map<String, Object> result = new LinkedHashMap<>(permission);
        result.put(
                "multiObjectPermission",
                multiObjectPermission(
                        type,
                        Boolean.TRUE.equals(result.get("allowJoin"))));
        return result;
    }

    private Map<String, Object> multiObjectPermission(
            String type,
            boolean enabled) {
        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("enabled", enabled);
        switch (normalize(type)) {
            case "mysql", "postgresql" -> describe(
                    permission,
                    "cross_table_join",
                    "允许跨表 JOIN",
                    "开启后可在可见表之间执行 JOIN 或多表查询；关闭时 MCP 只能执行单表查询。");
            case "redis" -> describe(
                    permission,
                    "multi_key_pattern",
                    "允许多 Key Pattern 关联",
                    "开启后可一次关联多个 key pattern 做排查；关闭时 MCP 单次只访问一个 key pattern。");
            case "rabbitmq" -> describe(
                    permission,
                    "multi_queue_access",
                    "允许多队列关联",
                    "开启后可关联多个可见队列分析堆积与消费关系；关闭时单次只访问一个队列。");
            case "elasticsearch" -> describe(
                    permission,
                    "cross_index_search",
                    "允许跨索引查询",
                    "开启后可一次查询多个可见 index；关闭时 MCP 单次只查询一个 index。");
            case "prometheus" -> describe(
                    permission,
                    "multi_metric_correlation",
                    "允许多指标关联",
                    "开启后可在一次诊断里关联多个 metric；关闭时 MCP 单次只查询一个 metric。");
            default -> describe(
                    permission,
                    "multi_object_access",
                    "允许多对象查询",
                    "开启后 MCP 可一次访问多个可见对象；关闭时单次只访问一个对象。");
        }
        return permission;
    }

    private void describe(Map<String, Object> permission,
                          String key,
                          String label,
                          String description) {
        permission.put("key", key);
        permission.put("label", label);
        permission.put("description", description);
    }
}
