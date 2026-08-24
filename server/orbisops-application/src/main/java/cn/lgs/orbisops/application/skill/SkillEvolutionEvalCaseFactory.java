package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adds routing-positive, routing-negative and hard-case regression cases to an authored candidate. */
public final class SkillEvolutionEvalCaseFactory {

    public Map<String, Object> enrich(
            Map<String, Object> authored,
            List<SkillExperienceConsolidationSample> samples) {
        Map<String, Object> result = new LinkedHashMap<>(
                authored == null ? Map.of() : authored);
        List<Map<String, Object>> cases = new ArrayList<>(
                maps(result.get("evalCases")));
        Map<String, Object> routing = routingProfile(
                maps(result.get("changes")));
        for (String scenario : strings(routing.get("whenToUse"))) {
            cases.add(routingCase("POSITIVE", scenario, true));
        }
        for (String scenario : strings(routing.get("whenNotToUse"))) {
            cases.add(routingCase("NEGATIVE", scenario, false));
        }
        for (SkillExperienceConsolidationSample sample :
                samples == null ? List.<SkillExperienceConsolidationSample>of() : samples) {
            if (!sample.hardCase() || sample.summary().isBlank()) continue;
            cases.add(hardCase(sample));
        }
        List<Map<String, Object>> distinctCases = deduplicate(cases);
        result.put("evalCases", distinctCases);

        List<Map<String, Object>> artifacts = new ArrayList<>(
                maps(result.get("artifacts")));
        artifacts.removeIf(item ->
                "evals/routing-boundary-cases.json".equals(text(item.get("path"))));
        artifacts.add(Map.of(
                "path", "evals/routing-boundary-cases.json",
                "role", "EVAL",
                "content", CanonicalJson.stringifyPreservingOrder(distinctCases)));
        result.put("artifacts", List.copyOf(artifacts));
        return Map.copyOf(result);
    }

    private Map<String, Object> routingCase(
            String type,
            String scenario,
            boolean expectedMatch) {
        return Map.of(
                "caseId", "route-" + type.toLowerCase() + "-"
                        + shortHash(scenario),
                "caseType", "ROUTING_" + type,
                "input", Map.of("query", scenario),
                "expected", Map.of(
                        "routeShouldMatch", expectedMatch,
                        "requiresRoutingBoundary", true));
    }

    private Map<String, Object> hardCase(
            SkillExperienceConsolidationSample sample) {
        return Map.of(
                "caseId", "hard-case-" + shortHash(sample.observationId()),
                "caseType", "HARD_CASE",
                "sourceObservationId", sample.observationId(),
                "sourceRunId", sample.runId(),
                "input", Map.of("query", sample.summary()),
                "expected", Map.of(
                        "requiresEvidence", true,
                        "forbiddenPatterns", List.of(
                                "绕过审批",
                                "直接修改生产",
                                "关闭审计")));
    }

    private Map<String, Object> routingProfile(
            List<Map<String, Object>> changes) {
        for (Map<String, Object> change : changes) {
            if ("routingProfile".equals(text(change.get("section")))
                    && change.get("value") instanceof Map<?, ?> value) {
                return map(value);
            }
        }
        return Map.of();
    }

    private List<Map<String, Object>> deduplicate(
            List<Map<String, Object>> source) {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> item : source) {
            Map<String,Object> development=new LinkedHashMap<>(item);
            development.put("caseOrigin","AUTHORING_OR_DEVELOPMENT_SOURCE");
            development.put("usage","DEVELOPMENT_ONLY");
            unique.putIfAbsent(text(item.get("caseId")), Map.copyOf(development));
        }
        return List.copyOf(unique.values());
    }

    private String shortHash(String value) {
        return CanonicalObjectHasher.sha256Text(text(value)).substring(0, 12);
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) result.add(map(map));
        }
        return result;
    }

    private Map<String, Object> map(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            if (!text(item).isBlank()) result.add(text(item));
        }
        return List.copyOf(result);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
