package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEffectMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;

import java.util.Map;

/** Promotes a canary release after metric evaluation and guarded Skill publication. */
public final class SkillReleasePromotionCoordinator {

    private final SkillReleasePort releasePort;
    private final SkillPatchCandidateApplicationService candidateService;
    private final SkillEffectMetricApplicationService metricService;
    private final SkillManagementUseCase skillManagementUseCase;
    private final SkillReleasePackageAssembler packageAssembler;
    private final SkillReleaseStateCoordinator stateCoordinator;

    public SkillReleasePromotionCoordinator(SkillReleasePort releasePort,
                                            SkillPatchCandidateApplicationService candidateService,
                                            SkillEffectMetricApplicationService metricService,
                                            SkillManagementUseCase skillManagementUseCase,
                                            SkillReleasePackageAssembler packageAssembler,
                                            SkillReleaseStateCoordinator stateCoordinator) {
        if (releasePort == null || candidateService == null || metricService == null
                || skillManagementUseCase == null || packageAssembler == null || stateCoordinator == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_PROMOTION_DEPENDENCY_REQUIRED");
        }
        this.releasePort = releasePort;
        this.candidateService = candidateService;
        this.metricService = metricService;
        this.skillManagementUseCase = skillManagementUseCase;
        this.packageAssembler = packageAssembler;
        this.stateCoordinator = stateCoordinator;
    }

    public void promoteIfReady(SkillReleaseSnapshot release) {
        SkillReleaseSnapshot canary = requireCanary(release);
        SkillPatchCandidate candidate = candidateService.getCandidate(canary.candidateId());
        var evidence = releasePort.canaryEvidence(canary);
        if (evidence == null) return;
        String isolation = evidence.isolationReason();
        if (!isolation.isBlank()) {
            requireMove(stateCoordinator.move(canary, SkillReleaseStatus.CANARY, SkillReleaseStatus.ROLLED_BACK,
                    isolation, 0, "", ""));
            return;
        }
        if (!evidence.promotable()) return;
        if (!releasePort.claim(canary.releaseId(), SkillReleaseStatus.CANARY, SkillReleaseStatus.PROMOTING)) {
            return;
        }
        SkillReleaseSnapshot promoting = canary.transitionTo(SkillReleaseStatus.PROMOTING);
        Map<String, Object> candidatePatch = packageAssembler.patch(candidate);
        SkillPublicationOutcome published = publish(candidate, candidatePatch);
        String targetSkillId = candidate.targetSkillId();
        if (!published.published()) {
            requireMove(stateCoordinator.move(promoting, SkillReleaseStatus.PROMOTING,
                    SkillReleaseStatus.ROLLED_BACK,
                    published.reasonCode(),
                    0, "", targetSkillId));
            return;
        }
        requireMove(stateCoordinator.move(promoting, SkillReleaseStatus.PROMOTING,
                SkillReleaseStatus.ACTIVE,
                "CANARY_METRICS_PASSED",
                published.version(),
                published.skillHash(),
                published.skillId()));
    }

    private SkillPublicationOutcome publish(
            SkillPatchCandidate candidate,
            Map<String, Object> patch) {
        String targetSkillId = candidate.targetSkillId();
        if (!targetSkillId.isBlank()) {
            return skillManagementUseCase.publishEvolvedProjectSkillOutcome(
                    candidate.projectId(),
                    targetSkillId,
                    patch,
                    candidate.baseSkillVersion(),
                    candidate.baseSkillHash(),
                    "SYSTEM_SKILL_EVOLVER");
        }
        String candidateHash = candidate.candidateHash();
        if (candidateHash.length() < 16) throw new IllegalStateException("SKILL_CANDIDATE_HASH_INVALID");
        patch.put("skillId", "evolved-" + candidateHash.substring(0, 16));
        patch.put("name", "自动沉淀运维方法");
        return skillManagementUseCase.createProjectSkillOutcome(
                candidate.projectId(), patch, "SYSTEM_SKILL_EVOLVER");
    }

    private SkillReleaseSnapshot requireCanary(SkillReleaseSnapshot release) {
        if (release == null) throw new IllegalArgumentException("SKILL_RELEASE_REQUIRED");
        if (release.status() != SkillReleaseStatus.CANARY) {
            throw new IllegalStateException("SKILL_RELEASE_CANARY_REQUIRED:" + release.status().name());
        }
        return release;
    }

    private void requireMove(boolean moved) {
        if (!moved) throw new IllegalStateException("SKILL_RELEASE_STATE_CONFLICT");
    }
}
