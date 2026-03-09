package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEffectMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;

/** Rolls back an active Skill release when effect metrics degrade against its baseline. */
public final class SkillReleaseRollbackCoordinator {

    private final SkillReleasePort releasePort;
    private final SkillEffectMetricApplicationService metricService;
    private final SkillManagementUseCase skillManagementUseCase;
    private final SkillReleaseStateCoordinator stateCoordinator;

    public SkillReleaseRollbackCoordinator(
            SkillReleasePort releasePort,
            SkillEffectMetricApplicationService metricService,
            SkillManagementUseCase skillManagementUseCase,
            SkillReleaseStateCoordinator stateCoordinator) {
        if (releasePort == null
                || metricService == null
                || skillManagementUseCase == null
                || stateCoordinator == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_ROLLBACK_DEPENDENCY_REQUIRED");
        }
        this.releasePort = releasePort;
        this.metricService = metricService;
        this.skillManagementUseCase = skillManagementUseCase;
        this.stateCoordinator = stateCoordinator;
    }

    public void rollbackIfDegraded(SkillReleaseSnapshot release) {
        SkillReleaseSnapshot active = requireActive(release);
        String skillId = active.targetSkillId();
        int version = active.releasedVersion();
        if (skillId.isBlank() || version <= 0) return;
        SkillEffectMetrics metrics = metricService.metrics(
                active.projectId(), skillId, version);
        SkillEffectMetrics baseline = active.baselineVersion() > 0
                ? metricService.metrics(active.projectId(), skillId, active.baselineVersion())
                : SkillEffectMetrics.empty();
        String degradation = metricService.enoughSamples(metrics)
                ? metricService.degradationReason(metrics, baseline)
                : "";
        if (degradation.isBlank()
                || !releasePort.claim(
                        active.releaseId(),
                        SkillReleaseStatus.ACTIVE,
                        SkillReleaseStatus.ROLLING_BACK)) {
            return;
        }
        SkillReleaseSnapshot rollingBack = active.transitionTo(SkillReleaseStatus.ROLLING_BACK);
        if (rollingBack.baselineVersion() > 0) {
            skillManagementUseCase.rollbackProjectVersion(
                    rollingBack.projectId(),
                    skillId,
                    rollingBack.baselineVersion(),
                    "SYSTEM_SKILL_RELEASE_GUARD");
        }
        if (!stateCoordinator.move(
                rollingBack,
                SkillReleaseStatus.ROLLING_BACK,
                SkillReleaseStatus.ROLLED_BACK,
                degradation,
                version,
                rollingBack.releasedSkillHash(),
                skillId)) {
            throw new IllegalStateException("SKILL_RELEASE_STATE_CONFLICT");
        }
    }

    private SkillReleaseSnapshot requireActive(SkillReleaseSnapshot release) {
        if (release == null) throw new IllegalArgumentException("SKILL_RELEASE_REQUIRED");
        if (release.status() != SkillReleaseStatus.ACTIVE) {
            throw new IllegalStateException("SKILL_RELEASE_ACTIVE_REQUIRED:" + release.status().name());
        }
        return release;
    }
}
