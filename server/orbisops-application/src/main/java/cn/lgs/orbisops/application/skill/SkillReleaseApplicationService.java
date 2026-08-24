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
    private SkillAutomaticPublicationService automaticPublication;

    public SkillReleaseApplicationService withAutomaticPublication(SkillAutomaticPublicationService service) {
        this.automaticPublication = java.util.Objects.requireNonNull(service);
        return this;
    }

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

    public Map<String,Object> rollbackAtomic(String projectId,String candidateId,String actor,String reason) {
        if(automaticPublication==null) throw new IllegalStateException("SKILL_ATOMIC_PUBLICATION_UNAVAILABLE");
        automaticPublication.rollbackAtomic(projectId,candidateId,actor,reason);
        return SkillReleaseStartOutcome.released(releasePort.findByCandidate(candidateId).orElseThrow()).view();
    }

    public SkillReleaseStartOutcome startOutcome(String candidateId) {
        if (automaticPublication != null) return automaticPublication.start(candidateId);
        return starter.startOutcome(candidateId);
    }

    public SkillReleaseStartOutcome resumeRetained(String candidateId, Runnable leaseGuard) {
        if(automaticPublication==null) throw new IllegalStateException("SKILL_AUTOMATIC_PUBLICATION_REQUIRED");
        return automaticPublication.start(candidateId,leaseGuard);
    }

    public void evaluateReleases() {
        if (!settings.enabled()) return;
        for (SkillReleaseSnapshot release : releasePort.listEvaluable(
                settings.evaluationBatchSize())) {
            try {
                if (automaticPublication != null) {
                    if(SkillAutomaticPublicationService.POLICY.equals(release.metadata().get("publicationPolicy")))
                        automaticPublication.advance(release);
                    // Historical canary releases remain archival under the new publication policy.
                } else if (release.status() == SkillReleaseStatus.CANARY) {
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
