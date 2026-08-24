package cn.lgs.orbisops.application.skill;

import java.util.Map;
import java.util.Optional;

/** Persistence boundary for named Skill candidate validation results. */
public interface SkillPatchValidationResultPort {

    /** Read a completed check without rerolling a model verdict after downstream recovery. */
    record Checkpoint(String status, Map<String, Object> details) {
        public Checkpoint {
            status = status == null ? "" : status;
            details = Map.copyOf(details == null ? Map.of() : details);
        }
    }

    default Optional<Checkpoint> find(String candidateId, String validationType) {
        return Optional.empty();
    }

    void upsert(
            String candidateId,
            String validationType,
            String status,
            double score,
            Map<String, Object> result);
}
