package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.skill.SelectRuntimeSkillsQuery;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
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

    public OpsSkillCatalogExecutionDispatchHandler(
            SkillCatalogQueryService catalog,
            SelectRuntimeSkillsQuery runtimeSkills) {
        this.catalog = catalog;
        this.runtimeSkills = runtimeSkills;
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
            case "skill_search" -> Map.of(
                    "status", "SUCCEEDED",
                    "items", runtimeSkills.selectFrozen(new SelectRuntimeSkillsQuery.FrozenRequest(
                            frozenCatalog, text(arguments.get("query")),
                            intValue(arguments.get("limit"), 6))).selectedRefs());
            case "skill_load" -> load(projectId, arguments, frozenCatalog);
            default -> throw new IllegalArgumentException("未知 Skill 目录工具：" + target.toolName());
        };
    }

    private Map<String, Object> load(
            String projectId,
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
        Map<String, Object> skill = catalog.getSkillVersion(
                projectId, skillId, version, skillHash, packageHash, scope);
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
        if (StringUtils.hasText(artifactPath)) {
            Map<String, Object> artifact = new LinkedHashMap<>(catalog.getSkillArtifact(
                    projectId, skillId, version, skillHash, packageHash, scope, artifactPath));
            if ("BASE64".equals(fallback(artifact.get("encoding"), "UTF8"))) {
                artifact.remove("content");
                artifact.put("binaryContentAvailable", true);
                artifact.put("loadReason", "二进制资产不会注入模型上下文，可在管理端查看或导出");
            }
            loaded.put("artifact", Map.copyOf(artifact));
        } else {
            loaded.put("content", text(skill.get("content")));
        }
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
