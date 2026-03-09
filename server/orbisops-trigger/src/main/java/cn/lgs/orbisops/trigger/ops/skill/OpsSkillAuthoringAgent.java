package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.util.StringUtils;

@Service
public class OpsSkillAuthoringAgent {
    private final OpsAgentLlmClient llmClient;
    public OpsSkillAuthoringAgent(OpsAgentLlmClient llmClient) { this.llmClient = llmClient; }

    public Map<String, Object> author(Map<String, Object> input) {
        Map<String, Object> candidate = llmClient.available() ? fromLlm(input) : Map.of();
        return candidate.isEmpty() ? deterministic(input) : candidate;
    }

    private Map<String, Object> fromLlm(Map<String, Object> input) {
        JSONObject result = llmClient.chatJsonObject("skill-authoring-agent", """
                你是旁路 Skill Authoring Agent。你只能生成结构化方法候选，不能修改 ACTIVE Skill，不能授权工具，不能写审批或 approvedSnapshot。
                changes 必须是 [{"section":"routingProfile|routingRules|diagnosticRecipe|evidenceCriteria|negativeRules","operation":"upsert|remove","key":"...","value":{...}}]。
                每个非 NO_CHANGE 候选必须包含且只包含一个 section=routingProfile、operation=upsert、key=runtime 的 change；
                其 value 必须是 {"category":"DOCUMENT|DEVELOPMENT|DATA|OBSERVABILITY|OPERATIONS|COMMUNICATION|KNOWLEDGE|GENERAL","subcategory":"...","whenToUse":["具体触发场景"],"whenNotToUse":["明确禁用场景"],"keywords":["检索词"]}。
                whenToUse 和 whenNotToUse 都不能为空，且必须从输入案例与证据抽象，不能写成“任何问题”之类的宽泛边界。
                artifacts 可包含资源、脚本模板、报告模板和评测用例，格式为 [{"path":"resources/example.json|scripts/check.sh|templates/report.md|evals/cases.json","role":"RESOURCE|SCRIPT|TEMPLATE|EVAL","content":"..."}]。
                artifact 必须是文本，不得要求可执行权限；脚本只能用于审核前受控运行参考，不能包含生产写、凭据或绕过平台执行入口的命令。
                不得包含绕过审批/沙箱/执行中心、生产写、任意 SQL、重启生产、删除数据、关闭审计、扩大 MCP 权限。
                输出 JSON：{"patchType":"CREATE_SKILL|UPDATE_ROUTING_RULE|UPDATE_DIAGNOSTIC_RECIPE|UPDATE_EVIDENCE_CRITERIA|UPDATE_NEGATIVE_RULE|MERGE_SKILLS|SPLIT_SKILL|NO_CHANGE","riskLevel":"LOW|MEDIUM","changes":[],"artifacts":[],"evalCases":[],"reason":"..."}。
                """, JSON.toJSONString(input));
        if (result == null || result.getBooleanValue("degraded")) return Map.of();
        String patchType = text(result.get("patchType"), "NO_CHANGE").toUpperCase(Locale.ROOT);
        JSONArray changes = result.getJSONArray("changes");
        if ("NO_CHANGE".equals(patchType)) {
            return Map.of(
                    "patchType", "NO_CHANGE",
                    "riskLevel", "LOW",
                    "changes", List.of(),
                    "artifacts", List.of(),
                    "evalCases", List.of(),
                    "reason", text(result.get("reason"), "LLM_NO_REUSABLE_PATTERN"),
                    "authoringSource", "LLM");
        }
        if (changes == null || changes.isEmpty()) return Map.of();
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("patchType", patchType);
        candidate.put("riskLevel", text(result.get("riskLevel"), "LOW"));
        candidate.put("changes", new ArrayList<>(changes));
        candidate.put("artifacts", result.getJSONArray("artifacts") == null
                ? List.of() : new ArrayList<>(result.getJSONArray("artifacts")));
        candidate.put("evalCases", result.getJSONArray("evalCases") == null ? List.of() : new ArrayList<>(result.getJSONArray("evalCases")));
        candidate.put("reason", text(result.get("reason"), "LLM_STRUCTURED_AUTHORING"));
        candidate.put("authoringSource", "LLM");
        return candidate;
    }

    private Map<String, Object> deterministic(Map<String, Object> input) {
        List<?> evidence = input.get("toolEvidence") instanceof List<?> list ? list : List.of();
        List<?> evidenceRefs = input.get("evidenceRefs") instanceof List<?> list ? list : List.of();
        List<?> procedureHints = input.get("procedureHints") instanceof List<?> list ? list : List.of();
        String goal = text(input.get("normalizedUserGoal"));
        String finalOutput = text(input.get("finalOutput"), goal);
        if (procedureHints.isEmpty() && (evidence.isEmpty() || evidenceRefs.isEmpty()
                || !StringUtils.hasText(finalOutput) || finalOutput.length() < 80)) {
            return noChange("RULE_NO_REUSABLE_PATTERN");
        }
        Map<String, Object> rule = new LinkedHashMap<>();
        String procedure = procedureHints.stream().map(String::valueOf)
                .filter(StringUtils::hasText).limit(3).reduce((left, right) -> left + "；" + right).orElse("");
        String methodSource = StringUtils.hasText(procedure) ? procedure : finalOutput;
        rule.put("when", List.of(abbreviate(StringUtils.hasText(goal) ? goal : methodSource, 240)));
        rule.put("preferredSources", evidence.stream().limit(6).map(String::valueOf).toList());
        rule.put("mustHaveEvidence", List.of("真实 ToolResult resultId/outputHash", "明确时间范围和目标资源"));
        rule.put("mustNot", List.of("只凭 health 下结论", "证据不足时生成自动化变更"));
        rule.put("procedure", abbreviate(methodSource, 1600));
        String key = "diagnostic-" + stableHash(methodSource).substring(0, 12);
        List<Map<String, Object>> changes = List.of(
                Map.of(
                        "section", "routingProfile",
                        "operation", "upsert",
                        "key", "runtime",
                        "value", Map.of(
                                "category", "OBSERVABILITY",
                                "subcategory", "故障诊断",
                                "whenToUse", List.of(abbreviate(
                                        StringUtils.hasText(goal)
                                                ? goal
                                                : methodSource,
                                        240)),
                                "whenNotToUse", List.of(
                                        "纯知识问答",
                                        "缺少真实工具证据的生产变更请求"),
                                "keywords", List.of(
                                        "故障诊断",
                                        "日志",
                                        "指标",
                                        "证据"))),
                Map.of(
                        "section",
                        "diagnosticRecipe",
                        "operation",
                        "upsert",
                        "key",
                        key,
                        "value",
                        rule));
        List<Map<String, Object>> evalCases = List.of(Map.of(
                "caseId", "evidence-" + stableHash(methodSource).substring(0, 10),
                "sourceRunId", text(input.get("runId")),
                "input", Map.of("normalizedUserGoal", goal),
                "expected", Map.of(
                        "patchType", "UPDATE_DIAGNOSTIC_RECIPE",
                        "requiredChangeKeys", List.of(key),
                        "requiredArtifactPaths", List.of("resources/diagnostic-recipe.json"),
                        "requiresEvidence", true,
                        "forbiddenPatterns", List.of("绕过审批", "直接修改生产", "关闭审计"))));
        return Map.of(
                "patchType", "UPDATE_DIAGNOSTIC_RECIPE",
                "riskLevel", "LOW",
                "changes", changes,
                "artifacts", List.of(
                        Map.of("path", "resources/diagnostic-recipe.json", "role", "RESOURCE",
                                "content", JSON.toJSONString(changes)),
                        Map.of("path", "evals/regression-cases.json", "role", "EVAL",
                                "content", JSON.toJSONString(evalCases))),
                "evalCases", evalCases,
                "reason", "DETERMINISTIC_STRUCTURED_FALLBACK",
                "authoringSource", "RULE");
    }

    private Map<String, Object> noChange(String reason) {
        return Map.of("patchType", "NO_CHANGE", "riskLevel", "LOW", "changes", List.of(), "artifacts", List.of(),
                "evalCases", List.of(), "reason", reason, "authoringSource", "RULE");
    }

    private String stableHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Skill candidate key hash 计算失败", e);
        }
    }

    private String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String text(Object value) { return text(value, ""); }
    private String text(Object value, String fallback) { String text=value==null?"":String.valueOf(value).trim(); return text.isBlank()?fallback:text; }
}
