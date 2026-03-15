package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ProjectDefinitionApplicationService {

    private static final List<String> DEFAULT_ENVIRONMENTS = List.of("dev", "test", "prod");

    private final IProjectDefinitionRepository repository;
    private final ProjectKnowledgeBaseValidationPort knowledgeBaseValidationPort;

    public ProjectDefinitionApplicationService(
            IProjectDefinitionRepository repository,
            ProjectKnowledgeBaseValidationPort knowledgeBaseValidationPort) {
        if (repository == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_REPOSITORY_REQUIRED");
        }
        if (knowledgeBaseValidationPort == null) {
            throw new IllegalArgumentException("PROJECT_KNOWLEDGE_BASE_VALIDATION_PORT_REQUIRED");
        }
        this.repository = repository;
        this.knowledgeBaseValidationPort = knowledgeBaseValidationPort;
    }

    public Map<String, Object> create(Map<String, Object> request) {
        return view(createDefinition(request));
    }

    public ProjectDefinition createDefinition(Map<String, Object> request) {
        Map<String, Object> command = request == null ? Map.of() : request;
        String name = text(command.get("name"), "新业务系统");
        String projectId = uniqueId(normalizeId(text(command.get("projectId"), name)));
        String knowledgeBaseId = text(command.get("knowledgeBaseId"), "");
        knowledgeBaseValidationPort.validateIfConfigured(knowledgeBaseId);
        LocalDateTime now = LocalDateTime.now();
        ProjectDefinition definition = new ProjectDefinition(
                projectId,
                name,
                text(command.get("description"), "由运维平台创建的业务系统空间"),
                text(command.get("owner"), "ops"),
                values(command.get("environments"), DEFAULT_ENVIRONMENTS),
                knowledgeBaseId,
                text(command.get("defaultAgentId"), ""),
                values(command.get("skillIds"), List.of()),
                values(command.get("sharedMcpIds"), List.of()),
                true,
                now,
                now);
        return saved(definition);
    }

    public Map<String, Object> update(Map<String, Object> request) {
        return view(updateDefinition(request));
    }

    public ProjectDefinition updateDefinition(Map<String, Object> request) {
        Map<String, Object> command = request == null ? Map.of() : request;
        String projectId = required(command.get("projectId"), "PROJECT_ID_REQUIRED");
        ProjectDefinition current = requireDefinition(projectId);
        String knowledgeBaseId = text(command.get("knowledgeBaseId"), current.knowledgeBaseId());
        knowledgeBaseValidationPort.validateIfConfigured(knowledgeBaseId);
        ProjectDefinition updated = current.update(
                text(command.get("name"), current.name()),
                text(command.get("description"), current.description()),
                text(command.get("owner"), current.owner()),
                command.containsKey("environments")
                        ? values(command.get("environments"), DEFAULT_ENVIRONMENTS)
                        : current.environments(),
                knowledgeBaseId,
                text(command.get("defaultAgentId"), current.defaultAgentId()),
                command.containsKey("skillIds")
                        ? values(command.get("skillIds"), List.of())
                        : current.skillIds(),
                command.containsKey("sharedMcpIds")
                        ? values(command.get("sharedMcpIds"), List.of())
                        : current.sharedMcpIds(),
                LocalDateTime.now());
        return saved(updated);
    }

    public ProjectDefinition assignDefaultAgent(String projectId, String agentId) {
        ProjectDefinition current = requireDefinition(projectId);
        ProjectDefinition updated = current.update(
                current.name(),
                current.description(),
                current.owner(),
                current.environments(),
                current.knowledgeBaseId(),
                required(agentId, "PROJECT_DEFAULT_AGENT_REQUIRED"),
                current.skillIds(),
                current.sharedMcpIds(),
                LocalDateTime.now());
        return saved(updated);
    }

    public Optional<ProjectDefinition> findDefinition(String projectId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        Optional<ProjectDefinition> definition = repository.find(id);
        return definition == null ? Optional.empty() : definition;
    }

    public ProjectDefinition requireDefinition(String projectId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        return findDefinition(id)
                .orElseThrow(() -> new IllegalArgumentException("业务系统不存在：" + id));
    }

    public List<Map<String, Object>> listEnabled() {
        return listEnabledDefinitions().stream()
                .map(this::view)
                .toList();
    }

    public List<ProjectDefinition> listEnabledDefinitions() {
        List<ProjectDefinition> definitions = repository.listEnabled();
        return definitions == null ? List.of() : List.copyOf(definitions);
    }

    public Map<String, Object> find(String projectId) {
        return findDefinition(projectId).map(this::view).orElseGet(Map::of);
    }

    public boolean exists(String projectId) {
        String id = projectId == null ? "" : projectId.trim();
        return !id.isBlank() && repository.exists(id);
    }

    public String defaultAgentId(String projectId) {
        String id = projectId == null ? "" : projectId.trim();
        return id.isBlank()
                ? ""
                : repository.find(id).map(ProjectDefinition::defaultAgentId).orElse("");
    }

    public String defaultKnowledgeBaseId(String projectId) {
        String id = projectId == null ? "" : projectId.trim();
        return id.isBlank()
                ? ""
                : repository.find(id).map(ProjectDefinition::knowledgeBaseId).orElse("");
    }

    public List<String> environments(String projectId) {
        String id = projectId == null ? "" : projectId.trim();
        return id.isBlank()
                ? List.of()
                : repository.find(id)
                .map(ProjectDefinition::environments)
                .map(List::copyOf)
                .orElseGet(List::of);
    }

    public boolean owner(String projectId, String username, String userId) {
        String id = projectId == null ? "" : projectId.trim();
        if (id.isBlank()) {
            return false;
        }
        return repository.find(id)
                .map(definition -> matches(definition.owner(), username, userId))
                .orElse(false);
    }

    public Map<String, Object> view(ProjectDefinition definition) {
        if (definition == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", definition.projectId());
        result.put("name", definition.name());
        result.put("description", definition.description());
        result.put("owner", definition.owner());
        result.put("environments", definition.environments());
        result.put("knowledgeBaseId", definition.knowledgeBaseId());
        result.put("defaultAgentId", definition.defaultAgentId());
        result.put("skillIds", definition.skillIds());
        result.put("sharedMcpIds", definition.sharedMcpIds());
        result.put("enabled", definition.enabled());
        result.put("createdAt", time(definition.createdAt()));
        result.put("updatedAt", time(definition.updatedAt()));
        return result;
    }

    private String uniqueId(String baseId) {
        String candidate = baseId;
        while (repository.exists(candidate)) {
            candidate = baseId + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }

    private ProjectDefinition saved(ProjectDefinition definition) {
        ProjectDefinition saved = repository.save(definition);
        return saved == null ? definition : saved;
    }

    private boolean matches(String owner, String username, String userId) {
        String normalizedOwner = owner == null ? "" : owner.trim();
        return !normalizedOwner.isBlank()
                && ((!text(username, "").isBlank()
                && normalizedOwner.equalsIgnoreCase(text(username, "")))
                || (!text(userId, "").isBlank()
                && normalizedOwner.equalsIgnoreCase(text(userId, ""))));
    }

    private List<String> values(Object source, List<String> fallback) {
        if (source == null) {
            return fallback == null ? List.of() : List.copyOf(fallback);
        }
        List<String> result = new ArrayList<>();
        if (source instanceof Iterable<?> iterable) {
            iterable.forEach(item -> add(result, item));
        } else if (source.getClass().isArray()) {
            int length = Array.getLength(source);
            for (int index = 0; index < length; index++) {
                add(result, Array.get(source, index));
            }
        } else {
            String raw = String.valueOf(source);
            for (String item : raw.split(",")) {
                add(result, item);
            }
        }
        return result.stream().distinct().toList();
    }

    private void add(List<String> target, Object value) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }

    private String normalizeId(String input) {
        String value = text(input, "item").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "-");
        value = value.replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        return value.isBlank() ? "item" : value;
    }

    private String required(Object value, String error) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
