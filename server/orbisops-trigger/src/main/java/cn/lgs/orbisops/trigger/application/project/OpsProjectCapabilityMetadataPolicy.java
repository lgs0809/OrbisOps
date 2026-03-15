package cn.lgs.orbisops.trigger.application.project;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stateless ACL policy for project resource permissions and generated MCP tool metadata. */
public final class OpsProjectCapabilityMetadataPolicy {

    private OpsProjectCapabilityMetadataPolicy() {
    }

    public static Map<String, Object> enrichPermission(String resourceType,
                                                       Map<String, Object> permission) {
        Map<String, Object> result = permission == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(permission);
        result.put("multiObjectPermission", multiObjectPermission(
                resourceType, Boolean.TRUE.equals(result.get("allowJoin"))));
        return result;
    }

    public static Map<String, Object> multiObjectPermission(String resourceType,
                                                            boolean enabled) {
        String normalizedType = normalizeType(resourceType);
        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("enabled", enabled);
        switch (normalizedType) {
            case "mysql", "postgresql" -> {
                permission.put("key", "cross_table_join");
                permission.put("label", "允许跨表 JOIN");
                permission.put("description", "开启后可在可见表之间执行 JOIN 或多表查询；关闭时 MCP 只能执行单表查询。");
            }
            case "redis" -> {
                permission.put("key", "multi_key_pattern");
                permission.put("label", "允许多 Key Pattern 关联");
                permission.put("description", "开启后可一次关联多个 key pattern 做排查；关闭时 MCP 单次只访问一个 key pattern。");
            }
            case "rabbitmq" -> {
                permission.put("key", "multi_queue_access");
                permission.put("label", "允许多队列关联");
                permission.put("description", "开启后可关联多个可见队列分析堆积与消费关系；关闭时单次只访问一个队列。");
            }
            case "elasticsearch" -> {
                permission.put("key", "cross_index_search");
                permission.put("label", "允许跨索引查询");
                permission.put("description", "开启后可一次查询多个可见 index；关闭时 MCP 单次只查询一个 index。");
            }
            case "prometheus" -> {
                permission.put("key", "multi_metric_correlation");
                permission.put("label", "允许多指标关联");
                permission.put("description", "开启后可在一次诊断里关联多个 metric；关闭时 MCP 单次只查询一个 metric。");
            }
            default -> {
                permission.put("key", "multi_object_access");
                permission.put("label", "允许多对象查询");
                permission.put("description", "开启后 MCP 可一次访问多个可见对象；关闭时单次只访问一个对象。");
            }
        }
        return permission;
    }

    public static Map<String, Object> enrichTransportMetadata(String resourceType,
                                                              List<String> allowedActions,
                                                              Map<String, Object> transportConfig) {
        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("transportConfig", transportConfig == null
                ? Map.of()
                : new LinkedHashMap<>(transportConfig));
        attachRemoteToolMetadata(generated, resourceType, allowedActions);
        return map(generated.get("transportConfig"));
    }

    public static void attachRemoteToolMetadata(Map<String, Object> generated,
                                                String resourceType,
                                                List<String> allowedActions) {
        if (generated == null) {
            throw new IllegalArgumentException("PROJECT_MCP_GENERATED_CONFIG_REQUIRED");
        }
        Map<String, Object> metadata = remoteToolMetadataFor(resourceType, allowedActions);
        if (metadata.isEmpty()) {
            return;
        }
        List<Map<String, Object>> remoteTools = new ArrayList<>();
        for (Object item : metadata.values()) {
            if (item instanceof Map<?, ?> raw) {
                remoteTools.add(map(raw));
            }
        }
        generated.put("remoteToolMetadata", map(metadata));
        generated.put("remoteTools", listOfMaps(remoteTools));
        Map<String, Object> transportConfig = new LinkedHashMap<>(map(generated.get("transportConfig")));
        transportConfig.put("remoteToolMetadata", map(metadata));
        transportConfig.put("remoteTools", listOfMaps(remoteTools));
        generated.put("transportConfig", transportConfig);
    }

    private static Map<String, Object> remoteToolMetadataFor(String resourceType,
                                                             List<String> allowedActions) {
        if (!allowsRead(allowedActions)) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        switch (normalizeType(resourceType)) {
            case "mysql" -> {
                addRemoteTool(metadata, "mysql_health", "LOW");
                addRemoteTool(metadata, "query_slow_log", "LOW");
                addRemoteTool(metadata, "query_statement_digest", "LOW");
                addRemoteTool(metadata, "show_table_indexes", "LOW");
                addRemoteTool(metadata, "explain_select", "LOW");
            }
            case "postgresql" -> {
                addRemoteTool(metadata, "postgresql_health", "LOW");
                addRemoteTool(metadata, "query_pg_stat_statements", "LOW");
                addRemoteTool(metadata, "show_postgresql_indexes", "LOW");
                addRemoteTool(metadata, "explain_postgresql_select", "LOW");
            }
            case "redis" -> {
                addRemoteTool(metadata, "redis_health", "LOW");
                addRemoteTool(metadata, "redis_info", "LOW");
                addRemoteTool(metadata, "redis_get", "LOW");
                addRemoteTool(metadata, "redis_ttl", "LOW");
                addRemoteTool(metadata, "redis_scan", "MEDIUM");
            }
            case "rabbitmq" -> {
                addRemoteTool(metadata, "rabbitmq_health", "LOW");
                addRemoteTool(metadata, "rabbitmq_list_queues", "LOW");
                addRemoteTool(metadata, "rabbitmq_queue_detail", "LOW");
                addRemoteTool(metadata, "rabbitmq_list_consumers", "LOW");
            }
            case "elasticsearch" -> {
                addRemoteTool(metadata, "list_indices", "LOW");
                addRemoteTool(metadata, "get_mappings", "LOW");
                addRemoteTool(metadata, "search", "MEDIUM");
            }
            case "prometheus" -> {
                addRemoteTool(metadata, "prometheus_health", "LOW");
                addRemoteTool(metadata, "prometheus_metric_names", "LOW");
                addRemoteTool(metadata, "prometheus_query", "LOW");
                addRemoteTool(metadata, "prometheus_range_query", "LOW");
            }
            case "openapi" -> addRemoteTool(metadata, "openapi_list_operations", "LOW");
            default -> {
            }
        }
        return metadata;
    }

    private static void addRemoteTool(Map<String, Object> metadata,
                                      String remoteToolName,
                                      String riskLevel) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("toolName", remoteToolName);
        tool.put("remoteToolName", remoteToolName);
        tool.put("readOnly", true);
        tool.put("riskLevel", riskLevel);
        tool.put("allowedActions", List.of("READ"));
        tool.put("capability", "READ_ONLY");
        metadata.put(remoteToolName, tool);
    }

    private static boolean allowsRead(List<String> allowedActions) {
        if (allowedActions == null || allowedActions.isEmpty()) {
            return false;
        }
        return allowedActions.stream()
                .map(item -> text(item, "").toUpperCase(Locale.ROOT))
                .anyMatch(action -> action.contains("READ")
                        || action.contains("SELECT")
                        || action.contains("QUERY")
                        || action.contains("SEARCH")
                        || action.contains("LIST")
                        || action.contains("GET")
                        || action.contains("SHOW")
                        || action.contains("EXPLAIN"));
    }

    private static String normalizeType(String type) {
        String value = text(type, "mysql").toLowerCase(Locale.ROOT).replace("-", "_");
        if ("pgsql".equals(value) || "pg".equals(value)) return "postgresql";
        if ("elk".equals(value) || "es".equals(value)) return "elasticsearch";
        if ("k8s".equals(value)) return "kubernetes";
        if ("gitlab".equals(value)) return "gitlab_ci";
        if ("http".equals(value) || "api".equals(value)) return "http_api";
        return value;
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), valueTree(item)));
        return result;
    }

    private static List<Map<String, Object>> listOfMaps(Iterable<?> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (values == null) return result;
        for (Object value : values) {
            if (value instanceof Map<?, ?>) result.add(map(value));
        }
        return result;
    }

    private static Object valueTree(Object value) {
        if (value instanceof Map<?, ?>) return map(value);
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) result.add(valueTree(item));
            return result;
        }
        if (value instanceof Object[] array) {
            List<Object> result = new ArrayList<>();
            for (Object item : array) result.add(valueTree(item));
            return result;
        }
        return value;
    }

    private static String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
