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
        var evidence = releasePort.canaryEvidence(active);
        String degradation = evidence == null ? "" : evidence.isolationReason();
        if (degradation.isBlank()
                || !releasePort.claim(
                        active.releaseId(),
                        SkillReleaseStatus.ACTIVE,
                        SkillReleaseStatus.ROLLING_BACK)) {
            return;
        }
        SkillReleaseSnapshot rollingBack = active.transitionTo(SkillReleaseStatus.ROLLING_BACK);
        var recovery = skillManagementUseCase.recoverProjectRelease(rollingBack);
        if (!stateCoordinator.move(
                rollingBack,
                SkillReleaseStatus.ROLLING_BACK,
                recovery.restored() ? SkillReleaseStatus.ROLLED_BACK : SkillReleaseStatus.DISABLED,
                degradation + ":" + recovery.reason(),
                recovery.version(),
                recovery.skillHash(),
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
