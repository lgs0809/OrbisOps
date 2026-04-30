package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchValidationDecision;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/** Typed result of release admission, including fail-closed validation and shadow outcomes. */
public record SkillReleaseStartOutcome(
        String candidateId,
        String releaseId,
        Disposition disposition,
        SkillReleaseStatus releaseStatus,
        String statusCode,
        String reasonCode,
        int canaryPercent,
        Map<String, Object> details
) {

    public enum Disposition {
        DISABLED,
        VALIDATION_REJECTED,
        SHADOW_REJECTED,
        RELEASED
    }

    public SkillReleaseStartOutcome {
        candidateId = text(candidateId);
        releaseId = text(releaseId);
        if (disposition == null) throw new IllegalArgumentException("SKILL_RELEASE_START_DISPOSITION_REQUIRED");
        statusCode = required(statusCode, "SKILL_RELEASE_START_STATUS_REQUIRED");
        reasonCode = text(reasonCode);
        canaryPercent = Math.max(0, Math.min(100, canaryPercent));
        details = details == null || details.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(details));
        if (disposition == Disposition.RELEASED && releaseStatus == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_START_RELEASE_STATUS_REQUIRED");
        }
        if (disposition != Disposition.RELEASED && releaseStatus != null) {
            throw new IllegalArgumentException("SKILL_RELEASE_START_RELEASE_STATUS_FORBIDDEN");
        }
    }

    public static SkillReleaseStartOutcome disabled(String candidateId) {
        return new SkillReleaseStartOutcome(
                candidateId, "", Disposition.DISABLED, null,
                "DISABLED", "SKILL_EVOLUTION_DISABLED", 0, Map.of());
    }

    public static SkillReleaseStartOutcome validationRejected(
            String candidateId,
            SkillPatchValidationDecision decision) {
        if (decision == null) throw new IllegalArgumentException("SKILL_VALIDATION_DECISION_REQUIRED");
        return new SkillReleaseStartOutcome(
                candidateId, "", Disposition.VALIDATION_REJECTED, null,
                decision.status(), String.join(",", decision.failures()), 0,
                Map.of("valid", decision.valid(), "failures", decision.failures()));
    }

    public static SkillReleaseStartOutcome shadowRejected(
            SkillShadowEvaluationOutcome shadow) {
        if (shadow == null) throw new IllegalArgumentException("SKILL_SHADOW_OUTCOME_REQUIRED");
        return new SkillReleaseStartOutcome(
                shadow.candidateId(), "", Disposition.SHADOW_REJECTED, null,
                shadow.status(), shadow.reasonCode(), 0, shadow.view());
    }

    public static SkillReleaseStartOutcome released(SkillReleaseSnapshot release) {
        if (release == null) throw new IllegalArgumentException("SKILL_RELEASE_REQUIRED");
        return new SkillReleaseStartOutcome(
                release.candidateId(), release.releaseId(), Disposition.RELEASED,
                release.status(), release.status().name(), release.reasonCode(),
                release.canaryPercent(), Map.of());
    }

    public boolean released() {
        return disposition == Disposition.RELEASED;
    }

    public Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>(details);
        if (!releaseId.isBlank()) result.put("releaseId", releaseId);
        result.put("candidateId", candidateId);
        result.put("status", statusCode);
        result.put("canaryPercent", canaryPercent);
        if (!reasonCode.isBlank()) result.put("reasonCode", reasonCode);
        result.put("disposition", disposition.name());
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
