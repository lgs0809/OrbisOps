package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assembles a complete immutable Skill package from one structured candidate. */
public class SkillReleasePackageAssembler {

    private final SkillCatalogQueryService catalogQueryService;

    public SkillReleasePackageAssembler(
            SkillCatalogQueryService catalogQueryService) {
        if (catalogQueryService == null) {
            throw new IllegalArgumentException("SKILL_CATALOG_QUERY_SERVICE_REQUIRED");
        }
        this.catalogQueryService = catalogQueryService;
    }

    public Map<String, Object> patch(SkillPatchCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_REQUIRED");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("content", renderChanges(routingProfile(candidate.changes())));
        List<Map<String, Object>> artifacts = releaseArtifacts(candidate);
        if (!candidate.targetSkillId().isBlank()) {
            artifacts = mergeBaseArtifacts(
                    candidate.projectId(),
                    candidate.targetSkillId(),
                    candidate.baseSkillVersion(),
                    candidate.baseSkillHash(),
                    artifacts);
        }
        patch.put("artifacts", artifacts);
        patch.put("evalSuites", evalSuitePaths(artifacts));
        patch.put("description", description(routingProfile(candidate.changes())));
        patch.putAll(routingProfile(candidate.changes()));
        patch.put("sourceType", "EVOLVER");
        patch.put("sourceRunId", candidate.sourceRunId());
        patch.put("evolutionJobId", candidate.candidateId());
        patch.put("changeSummary", "独立成功来源达标并通过最低检查后自动发布");
        return patch;
    }

    /** Compatibility entry for persisted/open candidate projections. */
    public Map<String, Object> patch(Map<String, Object> candidate) {
        Map<String, Object> source = candidate == null ? Map.of() : candidate;
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("content", renderChanges(routingProfile(source)));
        List<Map<String, Object>> artifacts = releaseArtifacts(source);
        String targetSkillId = text(source.get("target_skill_id"));
        if (!targetSkillId.isBlank()) {
            artifacts = mergeBaseArtifacts(
                    text(source.get("project_id")),
                    targetSkillId,
                    number(source.get("base_skill_version")),
                    text(source.get("base_skill_hash")),
                    artifacts);
        }
        patch.put("artifacts", artifacts);
        patch.put("evalSuites", evalSuitePaths(artifacts));
        patch.put("description", description(routingProfile(source)));
        patch.putAll(routingProfile(source));
        patch.put("sourceType", "EVOLVER");
        patch.put("sourceRunId", text(source.get("source_run_id")));
        patch.put("evolutionJobId", text(source.get("candidate_id")));
        patch.put("changeSummary", "独立成功来源达标并通过最低检查后自动发布");
        return patch;
    }

    private Map<String, Object> routingProfile(
            Map<String, Object> candidate) {
        List<Map<String, Object>> changes = maps(candidate.get("changes"));
        if (changes.isEmpty()) {
            changes = maps(parseList(candidate.get("changes_json")));
        }
        for (Map<String, Object> change : changes) {
            if (!"routingProfile".equals(text(change.get("section")))
                    || !(change.get("value") instanceof Map<?, ?> value)) {
                continue;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            value.forEach((key, item) ->
                    result.put(String.valueOf(key), item));
            return result;
        }
        throw new IllegalArgumentException(
                "SKILL_ROUTING_PROFILE_REQUIRED");
    }

    private Map<String, Object> routingProfile(List<?> changes) {
        for (Map<String, Object> change : maps(changes)) {
            if (!"routingProfile".equals(text(change.get("section")))
                    || !(change.get("value") instanceof Map<?, ?> value)) {
                continue;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            value.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        throw new IllegalArgumentException("SKILL_ROUTING_PROFILE_REQUIRED");
    }

    public List<Map<String, Object>> mergeBaseArtifacts(
            String projectId,
            String skillId,
            int baseVersion,
            String baseSkillHash,
            List<Map<String, Object>> authored) {
        Map<String, Object> current = catalogQueryService.getProjectSkill(
                projectId,
                skillId);
        if (number(current.get("currentVersion")) != baseVersion
                || !text(baseSkillHash).equals(text(current.get("currentSkillHash")))) {
            return authored == null ? List.of() : List.copyOf(authored);
        }
        String packageHash = text(current.get("currentPackageHash"));
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> item : catalogQueryService.listSkillArtifacts(
                projectId,
                skillId,
                baseVersion,
                baseSkillHash,
                packageHash,
                "PROJECT")) {
            String path = text(item.get("path"));
            if (!path.isBlank() && !SkillPackageManifest.ENTRYPOINT.equals(path)) {
                merged.put(path, new LinkedHashMap<>(item));
            }
        }
        for (Map<String, Object> item : authored == null ? List.<Map<String, Object>>of() : authored) {
            String path = text(item.get("path"));
            if (!path.isBlank()) merged.put(path, new LinkedHashMap<>(item));
        }
        return new ArrayList<>(merged.values());
    }

    private List<Map<String, Object>> releaseArtifacts(SkillPatchCandidate candidate) {
        List<Map<String, Object>> result = new ArrayList<>(maps(candidate.artifacts()));
        if (result.stream().noneMatch(item ->
                "resources/method.json".equals(text(item.get("path"))))) {
            result.add(Map.of(
                    "path", "resources/method.json",
                    "role", "RESOURCE",
                    "content", CanonicalJson.stringify(candidate.changes())));
        }
        if (result.stream().noneMatch(item ->
                "evals/regression-cases.json".equals(text(item.get("path"))))
                && !candidate.evalCases().isEmpty()) {
            result.add(Map.of(
                    "path", "evals/regression-cases.json",
                    "role", "EVAL",
                    "content", CanonicalJson.stringify(candidate.evalCases())));
        }
        return result;
    }

    private List<Map<String, Object>> releaseArtifacts(Map<String, Object> candidate) {
        List<Map<String, Object>> result = new ArrayList<>(maps(candidate.get("artifacts")));
        if (result.stream().noneMatch(item ->
                "resources/method.json".equals(text(item.get("path"))))) {
            result.add(Map.of(
                    "path", "resources/method.json",
                    "role", "RESOURCE",
                    "content", text(candidate.get("changes_json"), "[]")));
        }
        if (result.stream().noneMatch(item ->
                "evals/regression-cases.json".equals(text(item.get("path"))))) {
            String evalCases = text(candidate.get("eval_cases_json"));
            if (!evalCases.isBlank() && !"[]".equals(evalCases)) {
                result.add(Map.of(
                        "path", "evals/regression-cases.json",
                        "role", "EVAL",
                        "content", evalCases));
            }
        }
        return result;
    }

    private List<String> evalSuitePaths(List<Map<String, Object>> artifacts) {
        return (artifacts == null ? List.<Map<String, Object>>of() : artifacts)
                .stream()
                .filter(item -> "EVAL".equalsIgnoreCase(text(item.get("role"))))
                .map(item -> text(item.get("path")))
                .filter(path -> !path.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> view = new LinkedHashMap<>();
            map.forEach((key, field) -> view.put(String.valueOf(key), field));
            result.add(view);
        }
        return result;
    }

    private Object parseList(Object value) {
        String json = text(value);
        if (json.isBlank()) return List.of();
        try {
            List<?> parsed = CanonicalJson.parseArray(json);
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private String description(Map<String,Object> routing) {
        return String.join("；", strings(routing.get("whenToUse")));
    }

    private List<String> strings(Object value) {
        return value instanceof List<?> items ? items.stream().map(String::valueOf).toList() : List.of();
    }

    private String renderChanges(Map<String,Object> routing) {
        String entry = """
                ---
                name: evolved-ops-skill
                autoGenerated: true
                ---

                # 方法入口

                本页是方法入口。使用前必须按需读取 `resources/method.json` 中的完整步骤、前置条件与验收规则；未读取时不能声称已使用本方法。
                回归用例位于 `evals/`，不能把用例当成本次任务的真实证据。脚本和模板按文件清单渐进读取。

                # 安全边界

                - 只指导审核前证据收集；生产动作必须进入 ChangePackage 和 LandingRuntime。
                - Skill 中的脚本是文本资源，不获得直接执行权限。
                """;
        return entry + "\n## 适用条件\n- " + String.join("\n- ", strings(routing.get("whenToUse")))
                + "\n\n## 不适用条件\n- " + String.join("\n- ", strings(routing.get("whenNotToUse"))) + "\n";
    }

    private int number(Object value) {
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(text(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return text(value, "");
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }
}
