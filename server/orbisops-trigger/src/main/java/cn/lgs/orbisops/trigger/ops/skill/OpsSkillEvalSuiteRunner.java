package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;
import cn.lgs.orbisops.domain.skill.service.SkillRoutingBoundaryPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillRoutingProfilePolicy;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic Skill package regression gate. LLM shadow may add scoring but cannot override this result. */
@Service
public class OpsSkillEvalSuiteRunner {

    private static final List<String> FORBIDDEN = List.of(
            "绕过审批", "绕过沙箱", "绕过执行中心", "直接修改生产", "直接重启生产",
            "关闭审计", "扩大 mcp 权限", "landingruntime 自由规划", "bypass approval");

    private final SkillCatalogQueryService catalogQueryService;
    private final SkillRoutingProfilePolicy routingProfilePolicy =
            new SkillRoutingProfilePolicy();
    private final SkillRoutingBoundaryPolicy routingBoundaryPolicy =
            new SkillRoutingBoundaryPolicy();

    public OpsSkillEvalSuiteRunner(SkillCatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    public Result run(Map<String, Object> candidate) {
        List<Map<String, Object>> changes = maps(parseList(candidate.get("changes_json")));
        List<Map<String, Object>> artifacts = maps(parseList(candidate.get("artifacts_json")));
        List<Map<String, Object>> cases = new ArrayList<>(maps(parseList(candidate.get("eval_cases_json"))));
        cases.addAll(artifactCases(artifacts));
        List<Map<String, Object>> evidence = maps(parseList(candidate.get("evidence_refs_json")));
        List<String> failures = new ArrayList<>();
        Map<String, Object> baseline = validateBaseline(candidate, failures);
        ReplayMaterial baselineMaterial = baselineMaterial(candidate, baseline);
        List<Map<String, Object>> replayChanges = mergeChanges(baselineMaterial.changes(), changes);
        List<Map<String, Object>> replayArtifacts = mergeArtifacts(baselineMaterial.artifacts(), artifacts);
        if (cases.isEmpty()) failures.add("REGRESSION_EVAL_CASES_MISSING");
        String candidateText = (JSON.toJSONString(replayChanges) + JSON.toJSONString(replayArtifacts)).toLowerCase(Locale.ROOT);
        FORBIDDEN.stream().filter(candidateText::contains)
                .forEach(value -> failures.add("REGRESSION_UNSAFE_CONTENT:" + value));

        List<Map<String, Object>> caseResults = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> evalCase : deduplicate(cases)) {
            index++;
            List<String> candidateReasons = evaluateCase(evalCase, candidate, replayChanges, replayArtifacts, evidence, candidateText);
            List<String> baselineReasons = baselineMaterial.available()
                    ? evaluateCase(evalCase, candidate, baselineMaterial.changes(), baselineMaterial.artifacts(), evidence,
                    (JSON.toJSONString(baselineMaterial.changes()) + JSON.toJSONString(baselineMaterial.artifacts())).toLowerCase(Locale.ROOT))
                    : List.of();
            boolean regression = baselineMaterial.available() && baselineReasons.isEmpty() && !candidateReasons.isEmpty();
            Map<String, Object> caseResult = new LinkedHashMap<>();
            caseResult.put("caseId", text(evalCase.get("caseId"), "case-" + index));
            caseResult.put("passed", candidateReasons.isEmpty());
            caseResult.put("reasonCodes", candidateReasons);
            caseResult.put("baselineAvailable", baselineMaterial.available());
            caseResult.put("baselinePassed", baselineMaterial.available() && baselineReasons.isEmpty());
            caseResult.put("baselineReasonCodes", baselineReasons);
            caseResult.put("regression", regression);
            caseResults.add(Map.copyOf(caseResult));
            failures.addAll(candidateReasons);
            if (regression) failures.add("BASELINE_TO_CANDIDATE_REGRESSION:" + caseResult.get("caseId"));
        }
        List<String> distinctFailures = failures.stream().distinct().toList();
        return new Result(distinctFailures.isEmpty(), distinctFailures,
                List.copyOf(caseResults), baseline, cases.size());
    }

    private ReplayMaterial baselineMaterial(Map<String, Object> candidate, Map<String, Object> baseline) {
        if (!"UPDATE".equals(baseline.get("mode"))) {
            return ReplayMaterial.empty();
        }
        String projectId = text(candidate.get("project_id"));
        String skillId = text(candidate.get("target_skill_id"));
        int version = number(candidate.get("base_skill_version"));
        String skillHash = text(candidate.get("base_skill_hash"));
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(skillId) || version <= 0
                || !StringUtils.hasText(skillHash)
                || version != number(baseline.get("currentVersion"))
                || !skillHash.equals(text(baseline.get("currentSkillHash")))) {
            return ReplayMaterial.empty();
        }
        try {
            Map<String, Object> current = catalogQueryService.getProjectSkill(projectId, skillId);
            String packageHash = text(first(current.get("currentPackageHash"), current.get("packageHash")));
            List<Map<String, Object>> artifacts = catalogQueryService.listSkillArtifacts(
                    projectId, skillId, version, skillHash, packageHash, "PROJECT");
            return new ReplayMaterial(true, artifactChanges(artifacts), artifacts);
        } catch (RuntimeException e) {
            return ReplayMaterial.empty();
        }
    }

    private List<Map<String, Object>> artifactChanges(List<Map<String, Object>> artifacts) {
        for (Map<String, Object> artifact : artifacts) {
            if (!"resources/method.json".equals(text(artifact.get("path")))) continue;
            try {
                Object parsed = JSON.parse(text(artifact.get("content")));
                if (parsed instanceof List<?> list) return maps(list);
                if (parsed instanceof Map<?, ?> map) return maps(parseList(map(map).get("changes")));
            } catch (RuntimeException ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> mergeChanges(List<Map<String, Object>> baseline,
                                                   List<Map<String, Object>> patch) {
        LinkedHashMap<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> item : baseline) merged.put(changeIdentity(item), item);
        for (Map<String, Object> item : patch) {
            String identity = changeIdentity(item);
            String operation = text(item.get("operation")).toUpperCase(Locale.ROOT);
            if (Set.of("DELETE", "REMOVE").contains(operation)) merged.remove(identity);
            else merged.put(identity, item);
        }
        return new ArrayList<>(merged.values());
    }

    private String changeIdentity(Map<String, Object> item) {
        return text(item.get("section")) + "\u0000" + text(item.get("key"));
    }

    private List<Map<String, Object>> mergeArtifacts(List<Map<String, Object>> baseline,
                                                     List<Map<String, Object>> patch) {
        LinkedHashMap<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> item : baseline) merged.put(text(item.get("path")), item);
        for (Map<String, Object> item : patch) {
            String path = text(item.get("path"));
            String operation = text(item.get("operation")).toUpperCase(Locale.ROOT);
            if (Set.of("DELETE", "REMOVE").contains(operation)) merged.remove(path);
            else merged.put(path, item);
        }
        return new ArrayList<>(merged.values());
    }

    private Map<String, Object> validateBaseline(Map<String, Object> candidate, List<String> failures) {
        String patchType = text(candidate.get("patch_type"));
        String targetSkillId = text(candidate.get("target_skill_id"));
        if ("CREATE_SKILL".equalsIgnoreCase(patchType) || !StringUtils.hasText(targetSkillId)) {
            return Map.of("mode", "CREATE", "targetSkillId", targetSkillId);
        }
        int baseVersion = number(candidate.get("base_skill_version"));
        String baseHash = text(candidate.get("base_skill_hash"));
        if (baseVersion <= 0 || !StringUtils.hasText(baseHash)) {
            failures.add("BASELINE_VERSION_OR_HASH_MISSING");
            return Map.of("mode", "UPDATE", "targetSkillId", targetSkillId);
        }
        try {
            Map<String, Object> skill = catalogQueryService.getProjectSkill(text(candidate.get("project_id")), targetSkillId);
            int currentVersion = number(first(skill.get("currentVersion"), skill.get("version")));
            String currentHash = text(first(skill.get("currentSkillHash"), skill.get("skillHash")));
            if (currentVersion != baseVersion || !baseHash.equals(currentHash)) failures.add("BASELINE_MVCC_CONFLICT");
            return Map.of("mode", "UPDATE", "targetSkillId", targetSkillId,
                    "baseVersion", baseVersion, "baseSkillHash", baseHash,
                    "currentVersion", currentVersion, "currentSkillHash", currentHash);
        } catch (RuntimeException e) {
            failures.add("BASELINE_SKILL_NOT_FOUND");
            return Map.of("mode", "UPDATE", "targetSkillId", targetSkillId,
                    "baseVersion", baseVersion, "baseSkillHash", baseHash);
        }
    }

    private List<String> evaluateCase(Map<String, Object> evalCase,
                                      Map<String, Object> candidate,
                                      List<Map<String, Object>> changes,
                                      List<Map<String, Object>> artifacts,
                                      List<Map<String, Object>> evidence,
                                      String candidateText) {
        List<String> reasons = new ArrayList<>();
        Object expectedRaw = evalCase.get("expected");
        if (expectedRaw == null) {
            reasons.add("EVAL_EXPECTATION_MISSING");
            return reasons;
        }
        Map<String, Object> expected = map(expectedRaw);
        if (expected.isEmpty()) {
            String statement = text(expectedRaw).toLowerCase(Locale.ROOT);
            if (statement.contains("evidence") || statement.contains("证据")) {
                if (evidence.isEmpty()) reasons.add("EVAL_REQUIRED_EVIDENCE_MISSING");
            } else if (!StringUtils.hasText(statement)) {
                reasons.add("EVAL_EXPECTATION_EMPTY");
            }
            return reasons;
        }
        String expectedPatchType = text(expected.get("patchType"));
        if (StringUtils.hasText(expectedPatchType)
                && !expectedPatchType.equalsIgnoreCase(text(candidate.get("patch_type")))) {
            reasons.add("EVAL_PATCH_TYPE_MISMATCH");
        }
        Set<String> changeKeys = new LinkedHashSet<>();
        changes.forEach(item -> changeKeys.add(text(item.get("key"))));
        for (String required : strings(expected.get("requiredChangeKeys"))) {
            if (!changeKeys.contains(required)) reasons.add("EVAL_REQUIRED_CHANGE_MISSING:" + required);
        }
        Set<String> artifactPaths = new LinkedHashSet<>();
        artifacts.forEach(item -> artifactPaths.add(text(item.get("path"))));
        for (String required : strings(expected.get("requiredArtifactPaths"))) {
            if (!artifactPaths.contains(required)) reasons.add("EVAL_REQUIRED_ARTIFACT_MISSING:" + required);
        }
        for (String forbidden : strings(expected.get("forbiddenPatterns"))) {
            if (candidateText.contains(forbidden.toLowerCase(Locale.ROOT))) {
                reasons.add("EVAL_FORBIDDEN_PATTERN_PRESENT:" + forbidden);
            }
        }
        if (bool(expected.get("requiresEvidence"), false) && evidence.isEmpty()) {
            reasons.add("EVAL_REQUIRED_EVIDENCE_MISSING");
        }
        if (expected.containsKey("routeShouldMatch")) {
            SkillRoutingProfile profile = routingProfile(changes);
            if (profile == null) {
                reasons.add("EVAL_ROUTING_PROFILE_MISSING");
            } else {
                String query = text(map(evalCase.get("input")).get("query"));
                boolean actual = routingBoundaryPolicy.match(
                        query,
                        profile).matched();
                boolean expectedMatch = bool(
                        expected.get("routeShouldMatch"),
                        false);
                if (actual != expectedMatch) {
                    reasons.add("EVAL_ROUTING_EXPECTATION_MISMATCH");
                }
            }
        }
        if (bool(expected.get("requiresRoutingBoundary"), false)
                && routingProfile(changes) == null) {
            reasons.add("EVAL_ROUTING_PROFILE_MISSING");
        }
        return reasons;
    }

    private SkillRoutingProfile routingProfile(
            List<Map<String, Object>> changes) {
        for (Map<String, Object> change : changes) {
            if (!"routingProfile".equals(
                    text(change.get("section")))) {
                continue;
            }
            Map<String, Object> value = map(change.get("value"));
            if (value.isEmpty()) return null;
            try {
                return routingProfilePolicy.requireProfile(
                        text(value.get("category")),
                        text(value.get("subcategory")),
                        "candidate",
                        "",
                        "",
                        value.get("whenToUse"),
                        value.get("whenNotToUse"),
                        value.get("keywords"));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<Map<String, Object>> artifactCases(List<Map<String, Object>> artifacts) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> artifact : artifacts) {
            if (!"EVAL".equalsIgnoreCase(text(artifact.get("role")))) continue;
            try {
                Object parsed = JSON.parse(text(artifact.get("content")));
                if (parsed instanceof List<?> list) result.addAll(maps(list));
                if (parsed instanceof Map<?, ?> map) {
                    Object cases = map(map).get("cases");
                    if (cases instanceof List<?> list) result.addAll(maps(list));
                }
            } catch (RuntimeException e) {
                result.add(Map.of("caseId", text(artifact.get("path")), "invalid", true));
            }
        }
        return result;
    }

    private List<Map<String, Object>> deduplicate(List<Map<String, Object>> cases) {
        LinkedHashMap<String, Map<String, Object>> unique = new LinkedHashMap<>();
        cases.forEach(item -> unique.putIfAbsent(JSON.toJSONString(item), item));
        return new ArrayList<>(unique.values());
    }

    private List<?> parseList(Object value) {
        if (value instanceof List<?> list) return list;
        return value instanceof String text && StringUtils.hasText(text) ? JSON.parseArray(text) : List.of();
    }
    private List<Map<String, Object>> maps(Iterable<?> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) if (item instanceof Map<?, ?> map) result.add(map(map));
        return result;
    }
    private Map<String, Object> map(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
    private List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        iterable.forEach(item -> { if (StringUtils.hasText(String.valueOf(item))) result.add(String.valueOf(item)); });
        return result;
    }
    private Object first(Object... values) { for (Object value : values) if (value != null) return value; return null; }
    private int number(Object value) { try { return value instanceof Number n ? n.intValue() : Integer.parseInt(text(value)); } catch (Exception e) { return 0; } }
    private boolean bool(Object value, boolean fallback) { return value instanceof Boolean b ? b : value == null ? fallback : Boolean.parseBoolean(text(value)); }
    private String text(Object value) { return text(value, ""); }
    private String text(Object value, String fallback) { String result = value == null ? "" : String.valueOf(value).trim(); return result.isEmpty() ? fallback : result; }

    public record Result(boolean passed, List<String> failures, List<Map<String, Object>> caseResults,
                         Map<String, Object> baseline, int caseCount) {
        public Map<String, Object> toMap() {
            return Map.of("passed", passed, "failures", failures, "caseResults", caseResults,
                    "baseline", baseline, "caseCount", caseCount);
        }
    }

    private record ReplayMaterial(boolean available, List<Map<String, Object>> changes,
                                  List<Map<String, Object>> artifacts) {
        private static ReplayMaterial empty() {
            return new ReplayMaterial(false, List.of(), List.of());
        }
    }
}
