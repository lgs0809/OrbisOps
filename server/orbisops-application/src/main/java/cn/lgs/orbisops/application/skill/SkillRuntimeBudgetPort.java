package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.service.SkillRuntimeBodyPolicy;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.util.List;

/** Atomically reserves unique version-bound payloads across nodes, retries and recovery of a Run. */
@FunctionalInterface
public interface SkillRuntimeBudgetPort {
    void reserve(String projectId, String runId, List<Load> loads);
    static String key(String project, String scope, String skillId, int version, String packageHash) {
        if (project == null || project.isBlank() || scope == null || skillId == null || skillId.isBlank()
                || version <= 0 || packageHash == null || packageHash.isBlank()) throw new IllegalArgumentException("SKILL_RUNTIME_BUDGET_IDENTITY_REQUIRED");
        return scope + ":" + project + ":" + skillId + ":" + version + ":" + packageHash;
    }
    record Load(String skillKey, String itemHash, int units) {
        public Load {
            if (skillKey == null || skillKey.isBlank() || itemHash == null || !itemHash.matches("[a-f0-9]{64}") || units < 0)
                throw new IllegalArgumentException("SKILL_RUNTIME_BUDGET_LOAD_INVALID");
        }
        public static Load body(String identity, String content) {
            return new Load(identity, CanonicalObjectHasher.sha256Text(identity + "\n" + content), SkillRuntimeBodyPolicy.units(content));
        }
    }
}
