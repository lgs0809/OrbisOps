package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.skill.SelectRuntimeSkillsQuery;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillRuntimeBudgetPort;
import cn.lgs.orbisops.domain.skill.service.SkillRuntimeBodyPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsSkillCatalogExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final SkillCatalogQueryService catalog;
    private final SelectRuntimeSkillsQuery runtimeSkills;
    private final SkillRuntimeBudgetPort budget;

    public OpsSkillCatalogExecutionDispatchHandler(
            SkillCatalogQueryService catalog,
            SelectRuntimeSkillsQuery runtimeSkills) {
        this(catalog, runtimeSkills, (project, run, loads) -> {
            throw new IllegalStateException("SKILL_RUNTIME_BUDGET_STORE_REQUIRED");
        });
    }

    @Autowired
    public OpsSkillCatalogExecutionDispatchHandler(SkillCatalogQueryService catalog,
            SelectRuntimeSkillsQuery runtimeSkills, SkillRuntimeBudgetPort budget) {
        this.catalog = catalog;
        this.runtimeSkills = runtimeSkills;
        this.budget = java.util.Objects.requireNonNull(budget);
    }

    @Override
    public String handlerId() {
        return "skill-catalog";
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return "skill.catalog".equals(target.toolsetId())
                || "SKILL".equalsIgnoreCase(target.adapterType());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        if (catalog == null || runtimeSkills == null) {
            throw new IllegalStateException("Skill Catalog application boundary 未初始化");
        }
        Map<String, Object> arguments = request.arguments();
        String projectId = required(first(request.requestContext().get("projectId"), arguments.get("projectId")),
                "Skill 工具必须绑定 projectId");
        List<Map<String, Object>> frozenCatalog = mapList(arguments.get("catalogRefs"));
        if (frozenCatalog.isEmpty()) throw new SecurityException("SKILL_CATALOG_NOT_BOUND_TO_WORK_SESSION");
        return switch (target.toolName()) {
            case "skill_search" -> search(projectId, arguments, frozenCatalog);
            case "skill_load" -> load(projectId, required(request.runId(), "SKILL_RUNTIME_BUDGET_RUN_REQUIRED"), arguments, frozenCatalog);
            default -> throw new IllegalArgumentException("未知 Skill 目录工具：" + target.toolName());
        };
    }

    private Map<String,Object> search(String projectId, Map<String,Object> arguments,
            List<Map<String,Object>> frozenCatalog) {
        int limit = Math.max(1, Math.min(20, intValue(arguments.get("limit"), 6)));
        var result = runtimeSkills.selectFrozen(new SelectRuntimeSkillsQuery.FrozenRequest(
                projectId, frozenCatalog, text(arguments.get("query")), limit));
        // Discovery must expose authorized metadata even when automatic application needs more context.
        // Loading remains frozen/versioned, permission checked and subject to the independent body budget.
        return Map.of("status", "SUCCEEDED", "items", result.catalogRefs().stream().limit(limit).toList(),
                "automaticSelection", result.selectedRefs(), "applicabilityNotes", result.suppressedRefs(),
                "usageBoundary", "Search results are candidate methods, not a decision that they apply. Read full conditions and referenced resources before use.");
    }

    private Map<String, Object> load(
            String projectId, String runId,
            Map<String, Object> arguments,
            List<Map<String, Object>> frozenCatalog) {
        String skillId = required(arguments.get("skillId"), "读取 Skill 必须提供 skillId");
        Map<String, Object> ref = frozenCatalog.stream()
                .filter(item -> skillId.equals(text(item.get("skillId"))))
                .findFirst()
                .orElseThrow(() -> new SecurityException("SKILL_NOT_BOUND_TO_WORK_SESSION：" + skillId));
        int version = intValue(ref.get("version"), 0);
        String skillHash = required(ref.get("skillHash"), "Skill hash 缺失");
        String packageHash = required(ref.get("packageHash"), "Skill packageHash 缺失");
        String scope = fallback(ref.get("scope"), "PROJECT");
        Map<String, Object> skill = catalog.getRuntimeSkillVersion(
                projectId, skillId, version, skillHash, packageHash, scope);
        String identity = SkillRuntimeBudgetPort.key(projectId, scope, skillId, version, packageHash);
        String body = new SkillRuntimeBodyPolicy().project(java.util.Objects.toString(skill.get("content"), ""),
                text(arguments.get("query")), SkillRuntimeBodyPolicy.TOTAL_BUDGET);
        List<SkillRuntimeBudgetPort.Load> loads = new ArrayList<>();
        loads.add(SkillRuntimeBudgetPort.Load.body(identity, body));
        String artifactPath = text(arguments.get("artifactPath"));
        Map<String, Object> loaded = new LinkedHashMap<>();
        loaded.put("status", "SUCCEEDED");
        loaded.put("skillId", skillId);
        loaded.put("version", ref.get("version"));
        loaded.put("skillHash", skillHash);
        loaded.put("packageHash", packageHash);
        loaded.put("artifactHashes", ref.getOrDefault("artifactHashes", Map.of()));
        loaded.put("name", fallback(first(skill.get("name"), skill.get("skillName")), skillId));
        loaded.put("description", text(skill.get("description")));
        loaded.put("artifacts", skill.getOrDefault("artifacts", List.of()));
        loaded.put("entrypoint", skill.getOrDefault("entrypoint", "SKILL.md"));
        loaded.put("content", body);
        loaded.put("bodyBudgetUnit", "UTF8_BYTE_UPPER_BOUND");
        if (StringUtils.hasText(artifactPath)) {
            Map<String, Object> artifact = new LinkedHashMap<>(catalog.getRuntimeSkillArtifact(
                    projectId, skillId, version, skillHash, packageHash, scope, artifactPath));
            if ("EVAL_SUITE".equalsIgnoreCase(text(artifact.get("role"))))
                throw new SecurityException("SKILL_EVALUATION_ARTIFACT_NOT_RUNTIME_VISIBLE");
            if ("BASE64".equals(fallback(artifact.get("encoding"), "UTF8"))) {
                artifact.remove("content");
                artifact.put("binaryContentAvailable", true);
                artifact.put("loadReason", "二进制资产不会注入模型上下文，可在管理端查看或导出");
            }
            if (artifact.containsKey("content") && !"SKILL.md".equals(artifactPath)) {
                String resource = java.util.Objects.toString(artifact.get("content"), "");
                if (SkillRuntimeBodyPolicy.units(resource) > SkillRuntimeBodyPolicy.TOTAL_BUDGET)
                    throw new IllegalStateException("SKILL_RESOURCE_BUDGET_EXCEEDED");
                loads.add(SkillRuntimeBudgetPort.Load.body(identity, artifactPath + "\n" + resource));
            }
            if ("SKILL.md".equals(artifactPath)) artifact.put("content", body);
            loaded.put("artifact", Map.copyOf(artifact));
        }
        budget.reserve(projectId, runId, loads);
        loaded.put("securityBoundary", "Skill 文件只提供审核前方法和资源，不授予脚本执行、工具权限或生产执行权限");
        return Map.copyOf(loaded);
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, itemValue) -> copy.put(String.valueOf(key), itemValue));
                result.add(copy);
            }
        }
        return List.copyOf(result);
    }

    private Object first(Object first, Object second) {
        return first != null ? first : second;
    }

    private String required(Object value, String message) {
        String normalized = text(value);
        if (!StringUtils.hasText(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String fallback(Object value, String fallback) {
        String normalized = text(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
