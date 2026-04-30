package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Final governance decision for one structured Skill patch candidate. */
public record SkillPatchValidationDecision(
        String status,
        boolean valid,
        List<String> failures) {

    public SkillPatchValidationDecision {
        status = status == null ? "VALIDATION_FAILED" : status.trim();
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
