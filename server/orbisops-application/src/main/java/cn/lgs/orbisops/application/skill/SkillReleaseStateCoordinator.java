package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;

import java.util.Map;

/** Coordinates typed release persistence, candidate projection and audit as one state transition. */
public final class SkillReleaseStateCoordinator {

    private final SkillReleasePort releasePort;
    private final SkillPatchCandidateApplicationService candidateService;
    private final SkillReleaseAuditPort auditPort;

    public SkillReleaseStateCoordinator(
            SkillReleasePort releasePort,
            SkillPatchCandidateApplicationService candidateService,
            SkillReleaseAuditPort auditPort) {
        if (releasePort == null || candidateService == null || auditPort == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_STATE_DEPENDENCY_REQUIRED");
        }
        this.releasePort = releasePort;
        this.candidateService = candidateService;
        this.auditPort = auditPort;
    }

    public boolean move(
            SkillReleaseSnapshot release,
            SkillReleaseStatus expectedStatus,
            SkillReleaseStatus status,
            String reason,
            int version,
            String hash,
            String skillId) {
        SkillReleaseSnapshot requiredRelease = required(release);
        SkillReleaseStatus expected = required(expectedStatus, "SKILL_RELEASE_EXPECTED_STATUS_REQUIRED");
        SkillReleaseStatus target = required(status, "SKILL_RELEASE_TARGET_STATUS_REQUIRED");
        expected.requireTransitionTo(target);
        if (requiredRelease.status() != expected) {
            throw new IllegalStateException(
                    "SKILL_RELEASE_SNAPSHOT_STATUS_MISMATCH:expected=" + expected.name()
                            + " actual=" + requiredRelease.status().name());
        }
        String effectiveSkillId = requiredRelease.effectiveTargetSkillId(skillId);
        String normalizedReason = text(reason);
        if (!releasePort.complete(
                requiredRelease.releaseId(),
                expected,
                target,
                normalizedReason,
                Math.max(0, version),
                text(hash),
                effectiveSkillId)) {
            return false;
        }
        candidateService.updateStatus(
                requiredRelease.candidateId(),
                SkillPatchCandidateStatus.require(target.name()),
                normalizedReason);
        auditPort.recordStateChanged(
                requiredRelease.projectId(),
                requiredRelease.agentId(),
                requiredRelease.releaseId(),
                target.name(),
                normalizedReason,
                effectiveSkillId,
                Map.of(
                        "reasonCode", normalizedReason,
                        "skillId", effectiveSkillId));
        return true;
    }

    private SkillReleaseSnapshot required(SkillReleaseSnapshot release) {
        if (release == null) throw new IllegalArgumentException("SKILL_RELEASE_REQUIRED");
        return release;
    }

    private SkillReleaseStatus required(SkillReleaseStatus status, String reasonCode) {
        if (status == null) throw new IllegalArgumentException(reasonCode);
        return status;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
