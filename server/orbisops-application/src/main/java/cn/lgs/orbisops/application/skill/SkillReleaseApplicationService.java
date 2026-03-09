package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;

import java.util.Map;

/** Stable application facade for Skill release start, promotion, rollback and reconciliation. */
public class SkillReleaseApplicationService {

    private final SkillReleasePort releasePort;
    private final SkillReleaseAuditPort auditPort;
    private final SkillReleaseSettings settings;
    private final SkillReleaseStarter starter;
    private final SkillReleasePromotionCoordinator promotionCoordinator;
    private final SkillReleaseRollbackCoordinator rollbackCoordinator;

    public SkillReleaseApplicationService(
            SkillReleasePort releasePort,
            SkillPatchCandidateApplicationService candidateService,
            SkillPatchValidationApplicationService validationService,
            SkillShadowApplicationService shadowService,
            SkillCanaryApplicationService canaryService,
            SkillEffectMetricApplicationService metricService,
            SkillManagementUseCase skillManagementUseCase,
            SkillReleasePackageAssembler packageAssembler,
            SkillReleaseAuditPort auditPort,
            SkillReleaseSettings settings) {
        if (releasePort == null || auditPort == null || settings == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_DEPENDENCY_REQUIRED");
        }
        SkillReleaseStateCoordinator stateCoordinator =
                new SkillReleaseStateCoordinator(
                        releasePort,
                        candidateService,
                        auditPort);
        this.releasePort = releasePort;
        this.auditPort = auditPort;
        this.settings = settings;
        this.starter = new SkillReleaseStarter(
                releasePort,
                candidateService,
                validationService,
                shadowService,
                canaryService,
                auditPort,
                settings);
        this.promotionCoordinator = new SkillReleasePromotionCoordinator(
                releasePort,
                candidateService,
                metricService,
                skillManagementUseCase,
                packageAssembler,
                stateCoordinator);
        this.rollbackCoordinator = new SkillReleaseRollbackCoordinator(
                releasePort,
                metricService,
                skillManagementUseCase,
                stateCoordinator);
    }

    public Map<String, Object> start(String candidateId) {
        return startOutcome(candidateId).view();
    }

    public SkillReleaseStartOutcome startOutcome(String candidateId) {
        return starter.startOutcome(candidateId);
    }

    public void evaluateReleases() {
        if (!settings.enabled()) return;
        for (SkillReleaseSnapshot release : releasePort.listEvaluable(
                settings.evaluationBatchSize())) {
            try {
                if (release.status() == SkillReleaseStatus.CANARY) {
                    promoteIfReady(release);
                } else if (release.status() == SkillReleaseStatus.ACTIVE) {
                    rollbackIfDegraded(release);
                } else {
                    throw new IllegalStateException(
                            "SKILL_RELEASE_NOT_EVALUABLE:" + release.status().name());
                }
            } catch (RuntimeException error) {
                String message = text(error.getMessage());
                String reason = "RECONCILIATION_REQUIRED:" + message;
                releasePort.markReconciliationRequired(release.releaseId(), reason);
                auditPort.recordEvaluationFailed(
                        release.projectId(),
                        release.releaseId(),
                        message);
            }
        }
    }

    public void promoteIfReady(SkillReleaseSnapshot release) {
        promotionCoordinator.promoteIfReady(required(release));
    }

    public void rollbackIfDegraded(SkillReleaseSnapshot release) {
        rollbackCoordinator.rollbackIfDegraded(required(release));
    }

    private SkillReleaseSnapshot required(SkillReleaseSnapshot release) {
        if (release == null) throw new IllegalArgumentException("SKILL_RELEASE_REQUIRED");
        return release;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
