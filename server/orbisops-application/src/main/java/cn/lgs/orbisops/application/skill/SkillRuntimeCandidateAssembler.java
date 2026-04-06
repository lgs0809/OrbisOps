package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.domain.skill.service.SkillGovernancePolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import cn.lgs.orbisops.domain.skill.service.SkillRoutingProfilePolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.Map;
import java.util.TreeMap;

/** Converts the catalog read model into the typed runtime selection candidate. */
public final class SkillRuntimeCandidateAssembler {

    private static final String GLOBAL = "GLOBAL";
    private static final String UPDATE_MODE_AUTO = "AUTO";
    private final SkillRoutingProfilePolicy routingProfilePolicy = new SkillRoutingProfilePolicy();
    private final SkillGovernancePolicy governancePolicy = new SkillGovernancePolicy();

    public SkillRuntimeCandidate fromView(Map<String, Object> skill) {
        if (skill == null || skill.isEmpty()) {
            throw new IllegalArgumentException("SKILL_RUNTIME_VIEW_REQUIRED");
        }
        String skillId = text(skill.get("skillId"));
        int version = integer(
                firstNonNull(
                        skill.get("currentVersion"),
                        skill.get("version")),
                0);
        String skillHash = text(firstNonNull(
                skill.get("currentSkillHash"),
                skill.get("skillHash")));
        if (skillId.isBlank() || version <= 0 || skillHash.isBlank()) {
            throw new IllegalArgumentException(
                    "SKILL_RUNTIME_CANDIDATE_IDENTITY_REQUIRED");
        }
        SkillPackageManifest.Descriptor skillPackage = packageDescriptor(skill);
        return new SkillRuntimeCandidate(
                skillId,
                text(skill.get("projectId")),
                text(skill.get("scope")),
                fallback(firstNonNull(skill.get("name"), skill.get("skillName")), text(skill.get("skillId"))),
                text(skill.get("description")),
                version,
                skillHash,
                fallback(firstNonNull(skill.get("currentPackageHash"), skill.get("packageHash")), skillPackage.packageHash()),
                fallback(skill.get("manifestHash"), skillPackage.manifestHash()),
                artifactHashes(skill.get("artifactHashes"), skillPackage.artifactHashes()),
                fallback(skill.get("entrypoint"), skillPackage.entrypoint()),
                text(skill.get("status")),
                fallback(skill.get("updateMode"), fallback(skill.get("update_mode"), UPDATE_MODE_AUTO)),
                integer(skill.get("contentLength"), text(firstNonNull(skill.get("content"), skill.get("markdown"))).length()),
                routingProfilePolicy.profile(
                        text(skill.get("category")),
                        text(firstNonNull(skill.get("subcategory"), skill.get("subCategory"))),
                        fallback(firstNonNull(skill.get("name"), skill.get("skillName")), text(skill.get("skillId"))),
                        text(skill.get("description")),
                        text(firstNonNull(skill.get("content"), skill.get("markdown"))),
                        firstNonNull(skill.get("whenToUse"), skill.get("useCases")),
                        firstNonNull(skill.get("whenNotToUse"), skill.get("exclusions")),
                        firstNonNull(skill.get("keywords"), skill.get("capabilityHints"))),
                governancePolicy.governanceState(skill));
    }

    private SkillPackageManifest.Descriptor packageDescriptor(Map<String, Object> skill) {
        return SkillPackageManifest.markdown(
                fallback(skill.get("scope"), GLOBAL),
                text(skill.get("projectId")),
                text(skill.get("skillId")),
                fallback(firstNonNull(skill.get("name"), skill.get("skillName")), text(skill.get("skillId"))),
                text(skill.get("description")),
                integer(firstNonNull(skill.get("currentVersion"), skill.get("version")), 1),
                text(firstNonNull(skill.get("content"), skill.get("markdown"))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> artifactHashes(Object value, Map<String, String> fallback) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> hashes = new TreeMap<>();
            map.forEach((key, item) -> hashes.put(String.valueOf(key), text(item)));
            return Map.copyOf(hashes);
        }
        if (value instanceof String json && !json.trim().isEmpty()) {
            try {
                return artifactHashes(CanonicalJson.parseObject(json), fallback);
            } catch (Exception ignored) {
                // A malformed catalog projection cannot provide artifact identity.
            }
        }
        return fallback == null ? Map.of() : Map.copyOf(fallback);
    }

    private Object firstNonNull(Object left, Object right) {
        return left == null ? right : left;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String fallback(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
