package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectResourcePreparation;
import cn.lgs.orbisops.application.project.ProjectResourcePreparationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Coordinates credential policy, live schema discovery and permission preparation. */
@Component
public class OpsProjectResourcePreparationService {

    private final OpsProjectResourceCredentialPolicy credentialPolicy;
    private final OpsProjectResourceSchemaScanner schemaScanner;

    public OpsProjectResourcePreparationService(
            OpsProjectResourceCredentialPolicy credentialPolicy,
            OpsProjectResourceSchemaScanner schemaScanner) {
        if (credentialPolicy == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_CREDENTIAL_POLICY_REQUIRED");
        }
        if (schemaScanner == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_SCHEMA_SCANNER_REQUIRED");
        }
        this.credentialPolicy = credentialPolicy;
        this.schemaScanner = schemaScanner;
    }

    public ProjectResourcePreparation prepare(ProjectResourcePreparationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_PREPARATION_REQUEST_REQUIRED");
        }
        String type = normalizeType(request.type());
        Map<String, Object> credential = credentialPolicy.prepare(
                request.command(),
                type,
                request.partialUpdate(),
                request.existingCredential());
        Map<String, Object> schema = schemaScanner.scan(
                type,
                request.endpoint(),
                credentialPolicy.resolve(credential));
        Map<String, Object> permission = request.partialUpdate()
                ? refreshPermissionObjects(
                        type,
                        request.resourceId(),
                        schema,
                        request.existingPermission())
                : defaultPermission(type, request.resourceId(), schema);
        return new ProjectResourcePreparation(
                typeName(type),
                credential,
                schema,
                permission,
                schemaStatus(schema));
    }

    private Map<String, Object> defaultPermission(String type,
                                                  String resourceId,
                                                  Map<String, Object> schema) {
        List<String> actions = switch (normalizeType(type)) {
            case "mysql", "postgresql" -> List.of("SHOW_SCHEMA", "SELECT", "EXPLAIN");
            case "redis" -> List.of("INFO", "SCAN_PATTERN", "GET", "TTL");
            case "rabbitmq" -> List.of("READ_OVERVIEW", "READ_QUEUE", "READ_CONSUMER");
            case "elasticsearch" -> List.of("READ_MAPPING", "SEARCH_INDEX", "AGGREGATION");
            case "prometheus" -> List.of("QUERY_INSTANT", "QUERY_RANGE", "READ_METADATA");
            case "openapi" -> List.of("READ_OPENAPI");
            case "service_control" -> List.of(
                    "GET_SERVICE_STATUS",
                    "RESTART_SERVICE_DRY_RUN",
                    "RESTART_SERVICE",
                    "GET_OPERATION_RECEIPT");
            default -> List.of("READ");
        };
        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("resourceId", resourceId);
        permission.put("actions", actions);
        permission.put("objects", new ArrayList<>(objectNames(schema)));
        permission.put("maxRows", 100);
        permission.put("timeoutSeconds", timeoutByType(type));
        permission.put("allowJoin", false);
        permission.put("multiObjectPermission",
                OpsProjectCapabilityMetadataPolicy.multiObjectPermission(type, false));
        permission.put("maxRangeMinutes", 60);
        return permission;
    }

    private Map<String, Object> refreshPermissionObjects(String type,
                                                         String resourceId,
                                                         Map<String, Object> schema,
                                                         Map<String, Object> existingPermission) {
        Map<String, Object> existing = existingPermission == null
                ? Map.of()
                : existingPermission;
        if (existing.isEmpty()) {
            return defaultPermission(type, resourceId, schema);
        }
        Set<String> scannedObjects = objectNames(schema);
        List<String> currentObjects = stringList(existing.get("objects"), List.of());
        List<String> nextObjects = currentObjects.stream()
                .filter(scannedObjects::contains)
                .toList();
        Map<String, Object> permission = new LinkedHashMap<>(existing);
        permission.put("resourceId", resourceId);
        permission.put("actions", stringList(
                existing.get("actions"),
                stringList(defaultPermission(type, resourceId, schema).get("actions"), List.of())));
        permission.put("objects", nextObjects.isEmpty()
                ? new ArrayList<>(scannedObjects)
                : nextObjects);
        permission.putIfAbsent("maxRows", 100);
        permission.putIfAbsent("timeoutSeconds", timeoutByType(type));
        permission.putIfAbsent("allowJoin", false);
        permission.put("multiObjectPermission",
                OpsProjectCapabilityMetadataPolicy.multiObjectPermission(
                        type, Boolean.TRUE.equals(permission.get("allowJoin"))));
        permission.putIfAbsent("maxRangeMinutes", 60);
        return permission;
    }

    private Set<String> objectNames(Map<String, Object> schema) {
        Set<String> names = new LinkedHashSet<>();
        if (schema == null) {
            return names;
        }
        Object objects = schema.get("objects");
        if (objects instanceof List<?> list) {
            list.forEach(item -> {
                if (item instanceof Map<?, ?> map && map.get("name") != null) {
                    names.add(String.valueOf(map.get("name")));
                }
            });
        }
        return names;
    }

    private String schemaStatus(Map<String, Object> schema) {
        return schema != null && "live".equals(schema.get("source"))
                ? "SCANNED"
                : "PREVIEW";
    }

    private int timeoutByType(String type) {
        return switch (normalizeType(type)) {
            case "prometheus", "elasticsearch" -> 30;
            case "mysql", "postgresql" -> 15;
            case "redis", "rabbitmq", "openapi", "service_control" -> 10;
            default -> 20;
        };
    }

    private String typeName(String type) {
        return switch (normalizeType(type)) {
            case "mysql" -> "MySQL";
            case "postgresql" -> "PostgreSQL";
            case "redis" -> "Redis";
            case "rabbitmq" -> "RabbitMQ";
            case "elasticsearch" -> "Elasticsearch";
            case "prometheus" -> "Prometheus";
            case "openapi" -> "OpenAPI";
            case "service_control" -> "Service Control";
            case "grafana" -> "Grafana";
            case "kubernetes" -> "Kubernetes";
            case "nacos" -> "Nacos";
            case "jenkins" -> "Jenkins";
            case "gitlab_ci" -> "GitLab CI";
            case "cmdb" -> "CMDB";
            case "http_api" -> "HTTP API";
            case "webhook" -> "Webhook";
            case "custom" -> "自定义组件";
            default -> type;
        };
    }

    private String normalizeType(String type) {
        String value = text(type, "mysql").toLowerCase(Locale.ROOT).replace("-", "_");
        if ("pgsql".equals(value) || "pg".equals(value)) return "postgresql";
        if ("elk".equals(value) || "es".equals(value)) return "elasticsearch";
        if ("k8s".equals(value)) return "kubernetes";
        if ("gitlab".equals(value)) return "gitlab_ci";
        if ("http".equals(value) || "api".equals(value)) return "http_api";
        return value;
    }

    private List<String> stringList(Object value, List<String> fallback) {
        if (value instanceof List<?> raw) {
            List<String> result = raw.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .toList();
            return result.isEmpty() ? fallback : result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text);
        }
        return fallback;
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
