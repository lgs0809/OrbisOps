package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class OpsProjectSkillToolProvider {

    private final OpsToolExecutionService toolExecutionService;

    public OpsProjectSkillToolProvider(OpsToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    public ToolCallback build(String projectId,
                              String actor,
                              String runId,
                              List<Map<String, Object>> frozenCatalogRefs) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(runId)
                || frozenCatalogRefs == null || frozenCatalogRefs.isEmpty()) {
            throw new IllegalArgumentException("Skill 目录工具必须绑定 projectId、runId 和已固定目录引用");
        }
        List<Map<String, Object>> authoritativeRefs = frozenCatalogRefs.stream()
                .map(LinkedHashMap::new).map(Map::copyOf).toList();
        Function<SkillCatalogInput, String> function = input -> JSON.toJSONString(execute(
                projectId, actor, runId, authoritativeRefs, input));
        return FunctionToolCallback.builder("UseProjectSkill", function)
                .description("""
                        按需检索或读取本次 Work Session 启动时已固定的 Skill 版本。
                        action=search 时提供 query，可先查看少量 Skill 摘要；action=load 时提供 skillId，先读取入口正文和文件清单。
                        入口引用了方法资源时，使用前必须传 artifactPath 按路径读取完整步骤与验收条件；只读入口不等于已使用完整方法。
                        其他资源和脚本模板按需读取；评测用例不能当成本次真实证据。脚本内容仅供审核前方法参考，不获得执行权限。
                        不允许读取本次 Run 启动后新建或新发布的 Skill，也不能用 Skill 绕过 Tool Policy、ChangePackage 或 LandingRuntime。
                        """)
                .inputType(SkillCatalogInput.class)
                .build();
    }

    private Map<String, Object> execute(String projectId,
                                        String actor,
                                        String runId,
                                        List<Map<String, Object>> frozenCatalogRefs,
                                        SkillCatalogInput input) {
        String action = input == null ? "search" : text(input.getAction(), "search").toLowerCase();
        String toolName = switch (action) {
            case "search", "list" -> "skill_search";
            case "load", "read" -> "skill_load";
            default -> throw new IllegalArgumentException("不支持的 Skill action：" + action);
        };
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("catalogRefs", frozenCatalogRefs);
        if (input != null) {
            if (StringUtils.hasText(input.getQuery())) arguments.put("query", input.getQuery());
            if (StringUtils.hasText(input.getSkillId())) arguments.put("skillId", input.getSkillId());
            if (StringUtils.hasText(input.getArtifactPath())) arguments.put("artifactPath", input.getArtifactPath());
            if (input.getLimit() != null) arguments.put("limit", input.getLimit());
        }
        return toolExecutionService.execute(Map.of(
                "projectId", projectId,
                "userId", text(actor, "ops-agent"),
                "runId", runId,
                "executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name(),
                "toolsetId", "skill.catalog",
                "toolName", toolName,
                "arguments", arguments), actor);
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    public static class SkillCatalogInput {
        private String action;
        private String query;
        private String skillId;
        private String artifactPath;
        private Integer limit;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public String getSkillId() { return skillId; }
        public void setSkillId(String skillId) { this.skillId = skillId; }
        public String getArtifactPath() { return artifactPath; }
        public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }
}
