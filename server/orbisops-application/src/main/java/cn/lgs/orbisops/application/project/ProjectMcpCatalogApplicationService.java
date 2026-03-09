package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProjectMcpCatalogApplicationService {

    private final IProjectMcpRepository repository;

    public ProjectMcpCatalogApplicationService(IProjectMcpRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("PROJECT_MCP_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    public ProjectMcpDefinition save(Map<String, Object> source) {
        return save(definition(source));
    }

    public ProjectMcpDefinition save(ProjectMcpDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("PROJECT_MCP_DEFINITION_REQUIRED");
        ProjectMcpDefinition saved = repository.save(definition);
        return saved == null ? definition : saved;
    }

    public List<ProjectMcpDefinition> listAll() {
        List<ProjectMcpDefinition> values = repository.listAll();
        return values == null ? List.of() : List.copyOf(values);
    }

    public List<ProjectMcpDefinition> list(String projectId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        List<ProjectMcpDefinition> values = repository.list(id);
        return values == null ? List.of() : List.copyOf(values);
    }

    public List<ProjectMcpDefinition> listByTemplate(String templateId) {
        String id = required(templateId, "PROJECT_MCP_TEMPLATE_ID_REQUIRED");
        List<ProjectMcpDefinition> values = repository.listByTemplate(id);
        return values == null ? List.of() : List.copyOf(values);
    }

    public Optional<ProjectMcpDefinition> find(String projectId, String mcpId) {
        return repository.find(
                required(projectId, "PROJECT_ID_REQUIRED"),
                required(mcpId, "PROJECT_MCP_ID_REQUIRED"));
    }

    public Map<String, Object> view(ProjectMcpDefinition definition) {
        if (definition == null) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mcpId", definition.mcpId());
        view.put("toolId", definition.mcpId());
        view.put("mcpName", definition.mcpName());
        view.put("toolName", definition.mcpName());
        view.put("projectId", definition.projectId());
        view.put("resourceId", definition.resourceId());
        view.put("resourceType", definition.resourceType());
        view.put("transportType", definition.transportType());
        view.put("templateId", definition.templateId());
        Map<String, Object> transportConfig = definition.transportConfig();
        view.put("transportConfig", transportConfig);
        if (transportConfig.get("remoteToolMetadata") instanceof Map<?, ?> metadata) {
            view.put("remoteToolMetadata", map(metadata));
        }
        if (transportConfig.get("remoteTools") instanceof Iterable<?> remoteTools) {
            List<Object> tools = new ArrayList<>();
            remoteTools.forEach(tools::add);
            view.put("remoteTools", List.copyOf(tools));
        }
        view.put("connectionStatus", text(
                transportConfig.get("connectionStatus"), ""));
        view.put("policyStatus", text(
                transportConfig.get("policyStatus"), ""));
        view.put("discoveryError", text(
                transportConfig.get("discoveryError"), ""));
        view.put("allowedActions", definition.allowedActions());
        view.put("riskLevel", definition.riskLevel().name());
        view.put("readOnly", definition.readOnly());
        view.put("permissionPolicy", definition.permissionPolicy());
        view.put("requestTimeout", definition.requestTimeout());
        view.put("status", definition.status().name());
        view.put("createdAt", time(definition.createdAt()));
        view.put("updatedAt", time(definition.updatedAt()));
        return view;
    }

    private ProjectMcpDefinition definition(Map<String, Object> source) {
        Map<String, Object> command = source == null ? Map.of() : source;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = time(command.get("createdAt"), now);
        LocalDateTime updatedAt = time(command.get("updatedAt"), createdAt);
        return new ProjectMcpDefinition(
                required(command.get("mcpId"), "PROJECT_MCP_ID_REQUIRED"),
                text(command.get("mcpName"), text(command.get("toolName"), "")),
                required(command.get("projectId"), "PROJECT_ID_REQUIRED"),
                text(command.get("resourceId"), ""),
                text(command.get("resourceType"), "custom"),
                text(command.get("transportType"), "stdio"),
                text(command.get("templateId"), ""),
                map(command.get("transportConfig")),
                strings(command.get("allowedActions")),
                ProjectMcpRiskLevel.failClosed(text(command.get("riskLevel"), "HIGH")),
                bool(command.get("readOnly"), false),
                map(command.get("permissionPolicy")),
                integer(command.get("requestTimeout"), 30),
                ProjectMcpStatus.from(text(command.get("status"), "ENABLED")),
                createdAt,
                updatedAt);
    }

    private Map<String, Object> map(Object source) {
        if (!(source instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<String> strings(Object source) {
        if (source == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (source instanceof Iterable<?> iterable) {
            iterable.forEach(item -> add(result, item));
        } else {
            String raw = String.valueOf(source);
            for (String item : raw.split("[,，\\n]")) {
                add(result, item);
            }
        }
        return result.stream().distinct().toList();
    }

    private void add(List<String> target, Object value) {
        String normalized = text(value, "");
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = text(value, "");
        return normalized.isBlank() ? fallback
                : "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "enabled".equalsIgnoreCase(normalized);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private LocalDateTime time(Object value, LocalDateTime fallback) {
        String normalized = text(value, "");
        if (normalized.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(normalized.replace(' ', 'T'));
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String required(Object value, String error) {
        String normalized = text(value, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
