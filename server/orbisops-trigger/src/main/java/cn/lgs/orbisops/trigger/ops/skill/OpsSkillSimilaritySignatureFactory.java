package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;
import cn.lgs.orbisops.domain.skill.service.SkillRoutingProfilePolicy;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Candidate/Skill routing profile, signature, and changed-section projection. */
final class OpsSkillSimilaritySignatureFactory {

    private static final List<String> SKILL_SECTION_MARKERS = List.of(
            "routingRules",
            "diagnosticRecipe",
            "evidenceCriteria",
            "negativeRules");

    private final SkillRoutingProfilePolicy routingProfilePolicy;
    private final OpsSkillSimilarityTextMetrics metrics;

    OpsSkillSimilaritySignatureFactory() {
        this(new SkillRoutingProfilePolicy(), new OpsSkillSimilarityTextMetrics());
    }

    OpsSkillSimilaritySignatureFactory(
            SkillRoutingProfilePolicy routingProfilePolicy,
            OpsSkillSimilarityTextMetrics metrics) {
        this.routingProfilePolicy = routingProfilePolicy;
        this.metrics = metrics;
    }

    CandidateContext candidate(Map<String, Object> candidate) {
        Map<String, Object> effective = candidate == null ? Map.of() : candidate;
        SkillRoutingProfile profile = candidateProfile(effective);
        String patchType = metrics.text(effective.get("patchType"));
        String reason = metrics.text(effective.get("reason"));
        String signature = metrics.normalize(String.join(
                " ",
                profile.positiveText(),
                profile.negativeText(),
                patchType,
                reason,
                JSON.toJSONString(effective.getOrDefault("changes", List.of()))));
        return new CandidateContext(
                signature,
                profile,
                changeKeys(effective.get("changes")),
                patchType,
                reason);
    }

    SkillContext skill(Map<String, Object> skill) {
        Map<String, Object> effective = skill == null ? Map.of() : skill;
        String content = metrics.text(effective.get("content"));
        SkillRoutingProfile profile = routingProfilePolicy.profile(
                metrics.text(effective.get("category")),
                metrics.text(effective.get("subcategory")),
                metrics.text(effective.get("name")),
                metrics.text(effective.get("description")),
                content,
                effective.get("whenToUse"),
                effective.get("whenNotToUse"),
                effective.get("keywords"));
        String signature = metrics.normalize(String.join(
                " ",
                profile.positiveText(),
                profile.negativeText(),
                metrics.text(effective.get("skillId")),
                metrics.text(effective.get("name")),
                metrics.text(effective.get("description")),
                metrics.abbreviate(content, 12_000)));
        String currentHash = metrics.text(effective.get("currentSkillHash"));
        String cacheKey = currentHash.isBlank()
                ? metrics.text(effective.get("skillHash"))
                : currentHash;
        return new SkillContext(
                signature,
                profile,
                skillSectionKeys(content),
                metrics.text(effective.get("skillId")),
                cacheKey,
                metrics.text(effective.get("description")));
    }

    private SkillRoutingProfile candidateProfile(Map<String, Object> candidate) {
        for (Map<String, Object> change : maps(candidate.get("changes"))) {
            if (!"routingProfile".equals(metrics.text(change.get("section")))
                    || !(change.get("value") instanceof Map<?, ?> value)) {
                continue;
            }
            return routingProfilePolicy.requireProfile(
                    metrics.text(value.get("category")),
                    metrics.text(value.get("subcategory")),
                    "candidate",
                    metrics.text(candidate.get("reason")),
                    "",
                    value.get("whenToUse"),
                    value.get("whenNotToUse"),
                    value.get("keywords"));
        }
        throw new IllegalArgumentException("SKILL_ROUTING_PROFILE_REQUIRED");
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> source)) {
                continue;
            }
            Map<String, Object> mapped = new LinkedHashMap<>();
            source.forEach((key, field) ->
                    mapped.put(String.valueOf(key), field));
            result.add(mapped);
        }
        return result;
    }

    private Set<String> changeKeys(Object value) {
        Set<String> keys = new LinkedHashSet<>();
        if (!(value instanceof Iterable<?> iterable)) {
            return keys;
        }
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String section = metrics.text(map.get("section"));
            String key = metrics.text(map.get("key"));
            if (!section.isBlank() || !key.isBlank()) {
                keys.add(section + ":" + key);
            }
        }
        return keys;
    }

    private Set<String> skillSectionKeys(String content) {
        Set<String> keys = new LinkedHashSet<>();
        for (String marker : SKILL_SECTION_MARKERS) {
            if (content.contains(marker)) {
                keys.add(marker + ":");
            }
        }
        return keys;
    }

    record CandidateContext(
            String signature,
            SkillRoutingProfile profile,
            Set<String> sectionKeys,
            String patchType,
            String reason) {
    }

    record SkillContext(
            String signature,
            SkillRoutingProfile profile,
            Set<String> sectionKeys,
            String skillId,
            String cacheKey,
            String description) {
    }
}
