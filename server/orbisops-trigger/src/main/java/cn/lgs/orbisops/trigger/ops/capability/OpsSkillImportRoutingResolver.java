package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;
import cn.lgs.orbisops.domain.skill.service.SkillRoutingProfilePolicy;

import java.util.List;
import java.util.Map;

/** Resolves mandatory Skill V3 routing metadata without guessing missing boundaries. */
final class OpsSkillImportRoutingResolver {

    private final SkillRoutingProfilePolicy policy = new SkillRoutingProfilePolicy();

    Map<String, Object> resolve(String name,
                                String description,
                                String content,
                                Map<String, Object> supplied,
                                Map<String, Object> packaged) {
        Map<String, Object> hints = supplied == null ? Map.of() : supplied;
        Map<String, Object> manifest = packaged == null ? Map.of() : packaged;
        try {
            SkillRoutingProfile profile = policy.requireProfile(
                    firstText(text(hints.get("category")), text(manifest.get("category"))),
                    firstText(text(hints.get("subcategory")), text(manifest.get("subcategory"))),
                    name,
                    description,
                    content,
                    firstPresent(hints, manifest, "whenToUse", "useCases"),
                    firstPresent(hints, manifest, "whenNotToUse", "exclusions"),
                    firstPresent(hints, manifest, "keywords", "capabilityHints"));
            return policy.toMap(profile);
        } catch (IllegalArgumentException error) {
            String prefix = "SKILL_ROUTING_PROFILE_REQUIRED:";
            if (error.getMessage() == null || !error.getMessage().startsWith(prefix)) {
                throw error;
            }
            throw new RoutingMetadataRequired(
                    error.getMessage().substring(prefix.length()).split(","),
                    name,
                    description);
        }
    }

    private Object firstPresent(Map<String, Object> primary,
                                Map<String, Object> fallback,
                                String... keys) {
        for (String key : keys) {
            if (primary.containsKey(key) && primary.get(key) != null) return primary.get(key);
        }
        for (String key : keys) {
            if (fallback.containsKey(key) && fallback.get(key) != null) return fallback.get(key);
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static final class RoutingMetadataRequired extends IllegalArgumentException {
        private final List<String> requiredFields;
        private final String detectedName;
        private final String detectedDescription;

        RoutingMetadataRequired(String[] requiredFields,
                                String detectedName,
                                String detectedDescription) {
            super("SKILL_ROUTING_PROFILE_REQUIRED:" + String.join(",", requiredFields));
            this.requiredFields = List.of(requiredFields);
            this.detectedName = detectedName;
            this.detectedDescription = detectedDescription;
        }

        List<String> requiredFields() {
            return requiredFields;
        }

        String detectedName() {
            return detectedName;
        }

        String detectedDescription() {
            return detectedDescription;
        }

        String question() {
            if (requiredFields.contains("whenToUse")
                    && requiredFields.contains("whenNotToUse")) {
                return "导入内容缺少路由边界。请说明：1）什么情况下应使用这个 Skill；"
                        + "2）什么情况下明确不应使用。你可以直接在当前对话回复，"
                        + "例如“适用于季度汇报和路演；不适用于数据计算和后端开发”。";
            }
            if (requiredFields.contains("whenToUse")) {
                return "请补充这个 Skill 的适用场景（whenToUse），说明用户提出哪些需求时应选中它。";
            }
            return "请补充这个 Skill 的禁用场景（whenNotToUse），说明哪些相似需求不应选中它。";
        }
    }
}
