package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.intValue;
import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.stringList;
import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.text;

/** Projects typed project resources into MCP process environment variables. */
final class OpsProjectMcpEnvironmentProjector {
    private final OpsProjectMcpTypePolicy typePolicy;
    private final OpsProjectMcpEndpointParser endpointParser;
    private final OpsServiceControlEnvironmentProjector serviceControl =
            new OpsServiceControlEnvironmentProjector();
    OpsProjectMcpEnvironmentProjector(
            OpsProjectMcpTypePolicy typePolicy,
            OpsProjectMcpEndpointParser endpointParser) {
        if (typePolicy == null || endpointParser == null) {
            throw new IllegalArgumentException(
                    "PROJECT_MCP_ENVIRONMENT_DEPENDENCY_REQUIRED");
        }
        this.typePolicy = typePolicy;
        this.endpointParser = endpointParser;
    }
    Map<String, String> project(
            String type,
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> permission) {
        Map<String, Object> safeCredential = credential == null
                ? Map.of()
                : credential;
        Map<String, Object> safePermission = permission == null
                ? Map.of()
                : permission;
        return switch (typePolicy.normalize(type)) {
            case "mysql" -> mysql(endpoint, safeCredential, safePermission);
            case "postgresql" -> postgresql(
                    endpoint,
                    safeCredential,
                    safePermission);
            case "redis" -> redis(endpoint, safeCredential, safePermission);
            case "rabbitmq" -> rabbitMq(
                    endpoint,
                    safeCredential,
                    safePermission);
            case "elasticsearch" -> elasticsearch(
                    endpoint,
                    safeCredential,
                    safePermission);
            case "prometheus" -> prometheus(
                    endpoint,
                    safeCredential,
                    safePermission);
            case "openapi" -> openapi(endpoint);
            case "service_control" -> serviceControl.project(endpoint, safePermission);
            default -> Map.of();
        };
    }

    private Map<String, String> mysql(
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> permission) {
        URI uri = endpointParser.resourceUri(endpoint, "mysql");
        Map<String, String> env = new LinkedHashMap<>();
        env.put("MYSQL_MCP_MODE", "native");
        env.put("MYSQL_HOST", text(uri.getHost(), "127.0.0.1"));
        env.put("MYSQL_PORT", String.valueOf(
                uri.getPort() > 0 ? uri.getPort() : 3306));
        env.put("MYSQL_USER", text(
                credential.get("username"),
                typePolicy.defaultUsername("mysql")));
        env.put("MYSQL_PASSWORD", text(credential.get("password"), ""));
        String database = endpointParser.databaseName(endpoint, "mysql");
        env.put("MYSQL_DATABASE", database);
        if (!database.isBlank()) {
            env.put("MYSQL_MCP_ALLOWED_DATABASES", database);
            List<String> objects = stringList(permission.get("objects"), List.of());
            if (!objects.isEmpty()) {
                env.put(
                        "MYSQL_MCP_ALLOWED_TABLES",
                        String.join(",", objects.stream()
                                .map(item -> item.contains(".") ? item : database + "." + item)
                                .toList()));
            }
        }
        if (stringList(permission.get("actions"), List.of()).stream()
                .anyMatch(action -> "EXPLAIN".equalsIgnoreCase(action))) {
            env.put("MYSQL_MCP_ALLOW_EXPLAIN_SELECT", "true");
        }
        env.put(
                "MYSQL_MCP_MAX_LIMIT",
                String.valueOf(intValue(permission.get("maxRows"), 100)));
        env.put(
                "MYSQL_TIMEOUT_MS",
                String.valueOf(typePolicy.timeoutSeconds("mysql") * 1000));
        return env;
    }

    private Map<String, String> postgresql(
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> permission) {
        URI uri = endpointParser.resourceUri(endpoint, "postgresql");
        Map<String, String> env = new LinkedHashMap<>();
        env.put("POSTGRES_HOST", text(uri.getHost(), "127.0.0.1"));
        env.put("POSTGRES_PORT", String.valueOf(
                uri.getPort() > 0 ? uri.getPort() : 5432));
        env.put("POSTGRES_USER", text(
                credential.get("username"),
                typePolicy.defaultUsername("postgresql")));
        env.put("POSTGRES_PASSWORD", text(credential.get("password"), ""));
        env.put(
                "POSTGRES_DATABASE",
                text(
                        endpointParser.databaseName(endpoint, "postgresql"),
                        "postgres"));
        env.put(
                "POSTGRES_MCP_MAX_LIMIT",
                String.valueOf(intValue(permission.get("maxRows"), 100)));
        env.put(
                "POSTGRES_TIMEOUT_MS",
                String.valueOf(
                        typePolicy.timeoutSeconds("postgresql") * 1000));
        return env;
    }

    private Map<String, String> redis(
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> permission) {
        URI uri = endpointParser.resourceUri(endpoint, "redis");
        Map<String, String> env = new LinkedHashMap<>();
        env.put("REDIS_HOST", text(uri.getHost(), "127.0.0.1"));
        env.put("REDIS_PORT", String.valueOf(
                uri.getPort() > 0 ? uri.getPort() : 6379));
        env.put("REDIS_USERNAME", text(credential.get("username"), ""));
        env.put("REDIS_PASSWORD", text(credential.get("password"), ""));
        env.put(
                "REDIS_DB",
                text(endpointParser.databaseName(endpoint, "redis"), "0"));
        env.put(
                "REDIS_MCP_MAX_KEYS",
                String.valueOf(intValue(permission.get("maxRows"), 100)));
        List<String> objects = stringList(permission.get("objects"), List.of());
        if (!objects.isEmpty()) {
            env.put("REDIS_MCP_ALLOWED_KEY_PREFIXES", String.join(",", objects));
        }
        env.put(
                "REDIS_TIMEOUT_MS",
                String.valueOf(typePolicy.timeoutSeconds("redis") * 1000));
        return env;
    }

    private Map<String, String> rabbitMq(
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> permission) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("RABBITMQ_MANAGEMENT_URL", endpoint);
        env.put(
                "RABBITMQ_USERNAME",
                text(credential.get("username"), "guest"));
        env.put("RABBITMQ_PASSWORD", text(credential.get("password"), ""));
        env.put(
                "RABBITMQ_ALLOWED_QUEUES_JSON",
                JSON.toJSONString(stringList(
                        permission.get("objects"),
                        List.of())));
        env.put(
                "RABBITMQ_MCP_MAX_ROWS",
                String.valueOf(intValue(permission.get("maxRows"), 100)));
        env.put(
                "RABBITMQ_TIMEOUT_MS",
                String.valueOf(typePolicy.timeoutSeconds("rabbitmq") * 1000));
        return env;
    }

    private Map<String, String> elasticsearch(
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> permission) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("ES_HOST", endpoint);
        env.put("ES_API_KEY", text(credential.get("apiKey"), ""));
        env.put(
                "ES_MAX_SIZE",
                String.valueOf(intValue(permission.get("maxRows"), 100)));
        return env;
    }

    private Map<String, String> prometheus(
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> permission) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PROMETHEUS_URL", endpoint);
        env.put(
                "PROMETHEUS_USERNAME",
                text(credential.get("username"), ""));
        env.put(
                "PROMETHEUS_PASSWORD",
                text(credential.get("password"), ""));
        env.put(
                "PROMETHEUS_TIMEOUT_MS",
                String.valueOf(typePolicy.timeoutSeconds("prometheus") * 1000));
        env.put(
                "PROMETHEUS_MAX_RANGE_MINUTES",
                String.valueOf(intValue(
                        permission.get("maxRangeMinutes"),
                        60)));
        return env;
    }

    private Map<String, String> openapi(String endpoint) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("OPENAPI_URL", endpoint);
        env.put(
                "OPENAPI_TIMEOUT_MS",
                String.valueOf(typePolicy.timeoutSeconds("openapi") * 1000));
        return env;
    }
}
