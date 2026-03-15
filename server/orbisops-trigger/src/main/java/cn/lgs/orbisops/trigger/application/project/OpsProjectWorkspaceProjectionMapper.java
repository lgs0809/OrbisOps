package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectProductReadinessProjection;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjection;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionRequest;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsProjectWorkspaceProjectionMapper {

    public ProjectWorkspaceProjectionRequest request(
            Map<String, Object> project,
            List<Map<String, Object>> resources,
            List<Map<String, Object>> mcps,
            List<String> projectSkillIds,
            List<String> globalSkillIds,
            List<String> knowledgeBaseIds,
            List<KnowledgeBaseCatalogEntry> projectKnowledgeBases,
            List<KnowledgeBaseCatalogEntry> globalKnowledgeBases) {
        Map<String, Object> source = project == null ? Map.of() : project;
        String projectId = text(source.get("projectId"), "");
        ProjectWorkspaceProjectionRequest.Project descriptor = new ProjectWorkspaceProjectionRequest.Project(
                projectId,
                text(source.get("name"), ""),
                text(source.get("description"), ""),
                text(source.get("owner"), ""),
                stringList(source.get("environments"), List.of("dev", "test", "prod")),
                text(source.get("knowledgeBaseId"), ""),
                text(source.get("defaultAgentId"), ""),
                stringList(source.get("skillIds"), List.of()),
                stringList(source.get("sharedMcpIds"), List.of()),
                booleanValue(source.get("enabled"), true),
                text(source.get("createdAt"), ""),
                text(source.get("updatedAt"), ""));
        List<Map<String, Object>> resourceItems = resources == null ? List.of() : List.copyOf(resources);
        List<Map<String, Object>> mcpItems = mcps == null ? List.of() : List.copyOf(mcps);
        List<ProjectWorkspaceProjectionRequest.EvidenceSource> evidenceSources = new ArrayList<>();
        for (Map<String, Object> resource : resourceItems) {
            evidenceSources.add(new ProjectWorkspaceProjectionRequest.EvidenceSource(
                    text(resource.get("type"), ""),
                    text(resource.get("status"), "")));
        }
        for (Map<String, Object> mcp : mcpItems) {
            evidenceSources.add(new ProjectWorkspaceProjectionRequest.EvidenceSource(
                    text(mcp.get("resourceType"), ""),
                    text(mcp.get("status"), "")));
        }
        return new ProjectWorkspaceProjectionRequest(
                descriptor,
                skillReferences(projectSkillIds, "PROJECT_AUTHORIZED"),
                skillReferences(globalSkillIds, "GLOBAL_ENABLED"),
                knowledgeBaseIds,
                knowledgeBaseReferences(projectKnowledgeBases, projectId, false),
                knowledgeBaseReferences(globalKnowledgeBases, projectId, true),
                evidenceSources,
                resourceItems.size(),
                mcpItems.size());
    }

    public Map<String, Object> detail(
            ProjectWorkspaceProjection projection,
            Map<String, Object> rawProject,
            List<Map<String, Object>> resources,
            List<Map<String, Object>> mcps) {
        if (projection == null) {
            return Map.of();
        }
        ProjectWorkspaceProjectionRequest.Project project = projection.project();
        Map<String, Object> view = base(project, rawProject);
        view.put("projectSkills", projection.projectSkills().stream().map(this::skillView).toList());
        view.put("enabledGlobalSkills", projection.enabledGlobalSkills().stream().map(this::skillView).toList());
        view.put("resources", resourceViews(resources));
        view.put("generatedMcps", generatedMcpViews(mcps));
        view.put("knowledgeBaseIds", projection.knowledgeBaseIds());
        view.put("projectKnowledgeBases", projection.projectKnowledgeBases().stream()
                .map(this::knowledgeBaseView)
                .toList());
        view.put("enabledGlobalKnowledgeBases", projection.enabledGlobalKnowledgeBases().stream()
                .map(this::knowledgeBaseView)
                .toList());
        view.put("dataResourceCount", projection.dataResourceCount());
        view.put("sourceRepositoryCount", projection.sourceRepositoryCount());
        view.put("executionResourceCount", projection.executionResourceCount());
        view.put("resourceCount", projection.resourceCount());
        view.put("generatedMcpCount", projection.generatedMcpCount());
        view.put("defaultAgentPublished", projection.defaultAgentPublished());
        view.put("readyForInvestigation", projection.readyForInvestigation());
        view.put("readinessReason", projection.readinessReason());
        view.put("diagnosticScenarios", projection.diagnosticScenarios().stream()
                .map(this::diagnosticScenarioView)
                .toList());
        view.put("recommendedScenarioId", projection.recommendedScenarioId());
        view.put("onboarding", projection.onboarding().stream().map(this::onboardingView).toList());
        return view;
    }

    public Map<String, Object> catalog(ProjectWorkspaceProjection projection) {
        if (projection == null) {
            return Map.of();
        }
        ProjectWorkspaceProjectionRequest.Project project = projection.project();
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("projectId", project.projectId());
        catalog.put("name", project.name());
        catalog.put("description", project.description());
        catalog.put("defaultAgentId", project.defaultAgentId());
        catalog.put("environments", project.environments());
        catalog.put("defaultAgentPublished", projection.defaultAgentPublished());
        catalog.put("readyForInvestigation", projection.readyForInvestigation());
        catalog.put("readinessReason", projection.readinessReason());
        catalog.put("diagnosticScenarios", projection.diagnosticScenarios().stream()
                .map(this::diagnosticScenarioView)
                .toList());
        catalog.put("recommendedScenarioId", projection.recommendedScenarioId());
        return Map.copyOf(catalog);
    }

    private Map<String, Object> base(
            ProjectWorkspaceProjectionRequest.Project project,
            Map<String, Object> rawProject) {
        Map<String, Object> view = new LinkedHashMap<>(rawProject == null ? Map.of() : rawProject);
        view.put("projectId", project.projectId());
        view.put("name", project.name());
        view.put("description", project.description());
        view.put("owner", project.owner());
        view.put("environments", project.environments());
        view.put("knowledgeBaseId", project.knowledgeBaseId());
        view.put("defaultAgentId", project.defaultAgentId());
        view.put("skillIds", project.skillIds());
        view.put("sharedMcpIds", project.sharedMcpIds());
        view.put("enabled", project.enabled());
        view.put("createdAt", project.createdAt());
        view.put("updatedAt", project.updatedAt());
        return view;
    }

    private List<ProjectWorkspaceProjectionRequest.SkillReference> skillReferences(
            List<String> skillIds,
            String scope) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return skillIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(skillId -> new ProjectWorkspaceProjectionRequest.SkillReference(
                        skillId, skillId, skillId, scope))
                .toList();
    }

    private List<ProjectWorkspaceProjectionRequest.KnowledgeBaseReference> knowledgeBaseReferences(
            List<KnowledgeBaseCatalogEntry> entries,
            String projectId,
            boolean global) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .map(entry -> new ProjectWorkspaceProjectionRequest.KnowledgeBaseReference(
                        entry.key().kbId(),
                        entry.key().kbId(),
                        entry.name(),
                        entry.name(),
                        global ? projectId : entry.key().projectId(),
                        global ? "GLOBAL_ENABLED" : entry.key().scope().name(),
                        entry.description(),
                        entry.status().name(),
                        value(entry.documentCount()),
                        value(entry.chunkCount()),
                        entry.sourceType(),
                        entry.createdAt(),
                        entry.updatedAt()))
                .toList();
    }

    private Map<String, Object> skillView(ProjectWorkspaceProjectionRequest.SkillReference skill) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("skillId", skill.skillId());
        view.put("name", skill.name());
        view.put("skillName", skill.skillName());
        view.put("scope", skill.scope());
        return view;
    }

    private Map<String, Object> knowledgeBaseView(
            ProjectWorkspaceProjectionRequest.KnowledgeBaseReference knowledgeBase) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("kbId", knowledgeBase.kbId());
        view.put("knowledgeTag", knowledgeBase.knowledgeTag());
        view.put("name", knowledgeBase.name());
        view.put("kbName", knowledgeBase.kbName());
        view.put("projectId", knowledgeBase.projectId());
        view.put("scope", knowledgeBase.scope());
        view.put("description", knowledgeBase.description());
        view.put("status", knowledgeBase.status());
        view.put("documentCount", knowledgeBase.documentCount());
        view.put("chunkCount", knowledgeBase.chunkCount());
        view.put("sourceType", knowledgeBase.sourceType());
        view.put("createTime", knowledgeBase.createTime());
        view.put("updateTime", knowledgeBase.updateTime());
        return view;
    }

    private Map<String, Object> diagnosticScenarioView(
            ProjectWorkspaceProjection.DiagnosticScenario scenario) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("scenarioId", scenario.scenarioId());
        view.put("name", scenario.name());
        view.put("description", scenario.description());
        view.put("promptTemplate", scenario.promptTemplate());
        view.put("ready", scenario.ready());
        view.put("unavailableReason", scenario.unavailableReason());
        view.put("allowedSources", scenario.allowedSources());
        return view;
    }

    public Map<String, Object> readiness(ProjectProductReadinessProjection.Readiness readiness) {
        if (readiness == null) return Map.of();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ready", readiness.ready());
        view.put("checks", readiness.checks().stream().map(check -> Map.of(
                "key", check.key(),
                "label", check.label(),
                "ready", check.ready(),
                "detail", check.detail())).toList());
        view.put("missing", readiness.missing());
        view.put("nextAction", readiness.nextAction());
        return view;
    }

    private Map<String, Object> onboardingView(ProjectWorkspaceProjection.OnboardingStep step) {
        return Map.of(
                "key", step.key(),
                "label", step.label(),
                "completed", step.completed(),
                "optional", step.optional());
    }

    private List<Map<String, Object>> resourceViews(List<Map<String, Object>> resources) {
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        return resources.stream().map(this::resourceView).toList();
    }

    private Map<String, Object> resourceView(Map<String, Object> resource) {
        Map<String, Object> view = new LinkedHashMap<>(resource == null ? Map.of() : resource);
        view.put("credential", credentialView(map(view.get("credential"))));
        view.put("schema", schemaView(map(view.get("schema"))));
        view.put("permission", permissionView(map(view.get("permission"))));
        return view;
    }

    private List<Map<String, Object>> generatedMcpViews(List<Map<String, Object>> mcps) {
        if (mcps == null || mcps.isEmpty()) {
            return List.of();
        }
        return mcps.stream().map(this::generatedMcpView).toList();
    }

    private Map<String, Object> generatedMcpView(Map<String, Object> generated) {
        Map<String, Object> view = new LinkedHashMap<>(generated == null ? Map.of() : generated);
        view.put("toolId", text(view.get("toolId"), text(view.get("mcpId"), "")));
        view.put("toolName", text(view.get("toolName"), text(view.get("mcpName"), "")));
        view.put("templateId", text(view.get("templateId"), ""));
        view.put("allowedActions", stringList(view.get("allowedActions"), List.of()));
        view.put("riskLevel", text(view.get("riskLevel"), "HIGH"));
        view.put("readOnly", booleanValue(view.get("readOnly"), false));
        view.put("permissionPolicy", permissionView(map(view.get("permissionPolicy"))));
        Map<String, Object> config = map(view.get("transportConfig"));
        if (!config.isEmpty()) {
            Map<String, Object> safeConfig = new LinkedHashMap<>(config);
            safeConfig.put("credential", credentialView(map(safeConfig.get("credential"))));
            safeConfig.remove("runtimeEnv");
            if (!view.containsKey("remoteToolMetadata")
                    && safeConfig.get("remoteToolMetadata") instanceof Map<?, ?> metadata) {
                view.put("remoteToolMetadata", map(metadata));
            }
            if (!view.containsKey("remoteTools") && safeConfig.get("remoteTools") instanceof List<?> tools) {
                view.put("remoteTools", tools);
            }
            view.put("transportConfig", safeConfig);
        }
        return view;
    }

    private Map<String, Object> schemaView(Map<String, Object> schema) {
        Map<String, Object> view = new LinkedHashMap<>(schema);
        List<Map<String, Object>> objects = new ArrayList<>();
        Object rawObjects = schema.get("objects");
        if (rawObjects instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> object = new LinkedHashMap<>();
                    rawMap.forEach((key, value) -> object.put(String.valueOf(key), value));
                    normalizeStringListField(object, "columns");
                    normalizeStringListField(object, "indexes");
                    normalizeStringListField(object, "fields");
                    normalizeStringListField(object, "labels");
                    objects.add(object);
                }
            }
        }
        view.put("objects", objects);
        return view;
    }

    private Map<String, Object> permissionView(Map<String, Object> permission) {
        Map<String, Object> view = new LinkedHashMap<>(permission);
        view.put("actions", stringList(permission.get("actions"), List.of()));
        view.put("objects", stringList(permission.get("objects"), List.of()));
        return view;
    }

    private Map<String, Object> credentialView(Map<String, Object> credential) {
        Map<String, Object> view = new LinkedHashMap<>();
        String username = text(credential.get("username"), "");
        String passwordRef = text(credential.get("passwordRef"), "");
        boolean hasPassword = StringUtils.hasText(passwordRef);
        view.put("username", username);
        view.put("configured", Boolean.TRUE.equals(credential.get("configured"))
                || StringUtils.hasText(username)
                || hasPassword);
        view.put("passwordMasked", hasPassword ? "******" : "");
        view.put("passwordRef", passwordRef);
        view.put("updatedAt", credential.get("updatedAt"));
        return view;
    }

    private void normalizeStringListField(Map<String, Object> object, String field) {
        if (object.containsKey(field)) {
            object.put(field, stringList(object.get(field), List.of()));
        }
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

    private long value(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
