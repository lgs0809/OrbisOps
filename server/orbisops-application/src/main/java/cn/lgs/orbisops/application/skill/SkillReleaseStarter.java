package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import cn.lgs.orbisops.domain.skill.model.SkillPatchValidationDecision;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;

import java.util.Map;

/** Starts an idempotent Skill release after validation and shadow evaluation. */
public final class SkillReleaseStarter {

    private final SkillReleasePort releasePort;
    private final SkillPatchCandidateApplicationService candidateService;
    private final SkillPatchValidationApplicationService validationService;
    private final SkillShadowApplicationService shadowService;
    private final SkillCanaryApplicationService canaryService;
    private final SkillReleaseAuditPort auditPort;
    private final SkillReleaseSettings settings;

    public SkillReleaseStarter(
            SkillReleasePort releasePort,
            SkillPatchCandidateApplicationService candidateService,
            SkillPatchValidationApplicationService validationService,
            SkillShadowApplicationService shadowService,
            SkillCanaryApplicationService canaryService,
            SkillReleaseAuditPort auditPort,
            SkillReleaseSettings settings) {
        if (releasePort == null
                || candidateService == null
                || validationService == null
                || shadowService == null
                || canaryService == null
                || auditPort == null
                || settings == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_START_DEPENDENCY_REQUIRED");
        }
        this.releasePort = releasePort;
        this.candidateService = candidateService;
        this.validationService = validationService;
        this.shadowService = shadowService;
        this.canaryService = canaryService;
        this.auditPort = auditPort;
        this.settings = settings;
    }

    public Map<String, Object> start(String candidateId) {
        return startOutcome(candidateId).view();
    }

    public SkillReleaseStartOutcome startOutcome(String candidateId) {
        String normalizedCandidateId = required(candidateId, "SKILL_CANDIDATE_ID_REQUIRED");
        if (!settings.enabled()) {
            return SkillReleaseStartOutcome.disabled(normalizedCandidateId);
        }
        return releasePort.findByCandidate(normalizedCandidateId)
                .map(SkillReleaseStartOutcome::released)
                .orElseGet(() -> startNew(normalizedCandidateId));
    }

    private SkillReleaseStartOutcome startNew(String candidateId) {
        SkillPatchValidationDecision validation = validationService.validateDecision(candidateId);
        if (!validation.valid()) {
            return SkillReleaseStartOutcome.validationRejected(candidateId, validation);
        }
        SkillShadowEvaluationOutcome shadow = shadowService.evaluateOutcome(candidateId);
        if (!shadow.passed()) {
            return SkillReleaseStartOutcome.shadowRejected(shadow);
        }
        SkillPatchCandidate candidate = candidateService.getCandidate(candidateId);
        String releaseId = "skill-release-"
                + CanonicalObjectHasher.sha256Text(candidateId).substring(0, 32);
        SkillReleaseSnapshot release = new SkillReleaseSnapshot(
                releaseId,
                candidateId,
                candidate.projectId(),
                candidate.agentId(),
                candidate.targetSkillId(),
                SkillReleaseStatus.CANARY,
                canaryService.percent(),
                candidate.baseSkillVersion(),
                candidate.baseSkillHash(),
                "SHADOW_PASSED",
                0,
                "",
                Map.of(
                        "shadow", shadow.view(),
                        "observationContract", releasePort.observationContract(candidate),
                        "minimumSampleSize", settings.minimumSampleSize()));
        releasePort.create(release);
        candidateService.updateStatus(
                candidateId,
                SkillPatchCandidateStatus.CANARY,
                release.reasonCode());
        auditPort.recordStateChanged(
                release.projectId(),
                release.agentId(),
                releaseId,
                release.status().name(),
                release.reasonCode(),
                release.targetSkillId(),
                Map.of(
                        "candidateId", candidateId,
                        "canaryPercent", release.canaryPercent()));
        return SkillReleaseStartOutcome.released(release);
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

    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
