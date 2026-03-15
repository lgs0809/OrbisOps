package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparation;
import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparationRequest;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationPreparation;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationPreparationRequest;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds project MCP generation commands from resource and template read models. */
@Component
public class OpsProjectMcpGenerationPreparationFactory {

    public ProjectMcpGenerationPreparation prepare(
            ProjectMcpGenerationPreparationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PROJECT_MCP_PREPARATION_REQUEST_REQUIRED");
        }
        ProjectResourceDefinition resource = request.resource();
        String type = normalizeType(resource.type().value());
        Map<String, Object> permission = OpsProjectCapabilityMetadataPolicy.enrichPermission(
                type, new LinkedHashMap<>(resource.permission()));
        List<String> allowedActions = stringList(permission.get("actions"), List.of());
        Map<String, Object> transportConfig = new LinkedHashMap<>();
        transportConfig.put("generated", true);
        transportConfig.put("projectId", request.projectId());
        transportConfig.put("resourceId", request.resourceId());
        transportConfig.put("resourceType", type);
        transportConfig.put("endpoint", resource.endpoint());
        transportConfig.put("credential", credentialView(resource.credential()));
        transportConfig.put("permission", permission);
        transportConfig.put("schema", resource.schema());
        transportConfig.put("serverTemplate", type + "-policy-mcp");

        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("transportConfig", transportConfig);
        OpsProjectCapabilityMetadataPolicy.attachRemoteToolMetadata(
                generated, type, allowedActions);
        return new ProjectMcpGenerationPreparation(
                text(resource.name(), typeName(type))
                        + " " + request.profile() + " MCP",
                type,
                normalizeId(type + "-readonly-template"),
                "stdio",
                map(generated.get("transportConfig")),
                allowedActions,
                "LOW",
                true,
                permission,
                timeoutByType(type),
                "ENABLED");
    }

    public ProjectMcpTemplateGenerationPreparation prepareTemplate(
            ProjectMcpTemplateGenerationPreparationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "PROJECT_MCP_TEMPLATE_PREPARATION_REQUEST_REQUIRED");
        }
        ProjectResourceDefinition resource = request.resource();
        McpTemplateDefinition template = request.template();
        Map<String, Object> command = new LinkedHashMap<>(request.command());
        String resourceType = normalizeType(resource.type().value());
        Map<String, Object> permission = OpsProjectCapabilityMetadataPolicy.enrichPermission(
                resourceType, new LinkedHashMap<>(resource.permission()));
        Map<String, Object> requestedPolicy = map(command.get("permissionPolicy"));
        if (!requestedPolicy.isEmpty()) {
            permission.putAll(requestedPolicy);
            permission = OpsProjectCapabilityMetadataPolicy.enrichPermission(
                    resourceType, permission);
        }
        List<String> allowedActions = stringListAllowEmpty(
                command.get("allowedActions"));
        if (allowedActions.isEmpty()) {
            allowedActions = template.supportedActions().isEmpty()
                    ? stringList(permission.get("actions"), List.of())
                    : template.supportedActions();
        }
        Map<String, Object> transportConfig =
                new LinkedHashMap<>(template.defaultTransportConfig());
        transportConfig.putAll(map(command.get("resolvedTransportConfig")));
        transportConfig.put("generated", true);
        transportConfig.put("projectId", request.projectId());
        transportConfig.put("resourceId", request.resourceId());
        transportConfig.put("resourceType", resourceType);
        transportConfig.put("endpoint", resource.endpoint());
        transportConfig.put("credential", credentialView(resource.credential()));
        transportConfig.put("permission", permission);
        transportConfig.put("schema", resource.schema());
        transportConfig.put("serverTemplate", text(
                transportConfig.get("serverTemplate"),
                resourceType + "-policy-mcp"));
        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("transportConfig", transportConfig);
        OpsProjectCapabilityMetadataPolicy.attachRemoteToolMetadata(
                generated, resourceType, allowedActions);
        return new ProjectMcpTemplateGenerationPreparation(
                text(command.get("toolName"),
                        text(command.get("mcpName"),
                                text(resource.name(), typeName(resourceType))
                                        + " " + request.profile() + " MCP")),
                resourceType,
                template.templateId(),
                text(command.get("transportType"), template.transportType()),
                map(generated.get("transportConfig")),
                allowedActions,
                text(command.get("riskLevel"), template.riskLevel())
                        .toUpperCase(Locale.ROOT),
                booleanValue(command.get("readOnly"), template.readOnly()),
                permission,
                intValue(command.get("requestTimeout"), timeoutByType(resourceType)),
                normalizeStatus(text(command.get("status"), "ENABLED")));
    }

    private Map<String, Object> credentialView(Map<String, Object> credential) {
        Map<String, Object> view = new LinkedHashMap<>();
        String username = text(credential.get("username"), "");
        String passwordRef = text(credential.get("passwordRef"), "");
        boolean hasPassword = StringUtils.hasText(passwordRef);
        view.put("username", username);
        view.put("configured", Boolean.TRUE.equals(credential.get("configured"))
                || StringUtils.hasText(username) || hasPassword);
        view.put("passwordMasked", hasPassword ? "******" : "");
        view.put("passwordRef", passwordRef);
        view.put("updatedAt", credential.get("updatedAt"));
        return view;
    }

    private int timeoutByType(String type) {
        return switch (normalizeType(type)) {
            case "prometheus", "elasticsearch" -> 30;
            case "mysql", "postgresql" -> 15;
            case "redis", "rabbitmq", "openapi" -> 10;
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
        String value = text(type, "mysql")
                .toLowerCase(Locale.ROOT)
                .replace("-", "_");
        if ("pgsql".equals(value) || "pg".equals(value)) return "postgresql";
        if ("elk".equals(value) || "es".equals(value)) return "elasticsearch";
        if ("k8s".equals(value)) return "kubernetes";
        if ("gitlab".equals(value)) return "gitlab_ci";
        if ("http".equals(value) || "api".equals(value)) return "http_api";
        return value;
    }

    private String normalizeId(String value) {
        String normalized = text(value, "item")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return StringUtils.hasText(normalized) ? normalized : "item";
    }

    private String normalizeStatus(String status) {
        String normalized = text(status, "ENABLED").toUpperCase(Locale.ROOT);
        return Set.of("ENABLED", "DISABLED", "PENDING_REVIEW", "STALE", "REJECTED")
                .contains(normalized) ? normalized : "PENDING_REVIEW";
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return "true".equalsIgnoreCase(text)
                    || "1".equals(text)
                    || "yes".equalsIgnoreCase(text)
                    || "enabled".equalsIgnoreCase(text);
        }
        return fallback;
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private List<String> stringList(Object value, List<String> fallback) {
        if (value instanceof List<?> raw) {
            List<String> result = raw.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .filter(item -> !item.startsWith("{\"$ref\""))
                    .toList();
            return result.isEmpty() ? fallback : result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text);
        }
        return fallback;
    }

    private List<String> stringListAllowEmpty(Object value) {
        if (value instanceof List<?> raw) {
            return raw.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        return List.of();
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
