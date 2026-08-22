package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCanaryContextApplicationService;
import cn.lgs.orbisops.application.skill.SkillReleaseApplicationService;
import cn.lgs.orbisops.application.skill.SkillReleasePackageAssembler;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Trigger scheduler and compatibility facade for Skill release governance. */
@Service
public class OpsSkillReleaseService {

    private final SkillReleaseApplicationService releaseService;
    private final SkillCanaryContextApplicationService canaryContextService;
    private final SkillReleasePackageAssembler packageAssembler;

    public OpsSkillReleaseService(
            SkillReleaseApplicationService releaseService,
            SkillCanaryContextApplicationService canaryContextService,
            SkillReleasePackageAssembler packageAssembler) {
        this.releaseService = releaseService;
        this.canaryContextService = canaryContextService;
        this.packageAssembler = packageAssembler;
    }

    public Map<String, Object> start(String candidateId) {
        return releaseService.start(candidateId);
    }

    public List<Map<String, Object>> resolveCanaryRefs(
            String projectId,
            String agentId,
            String runId) {
        return canaryContextService.resolveRefs(projectId, agentId, runId);
    }

    public String canaryContext(
            String projectId,
            String agentId,
            String runId) {
        return canaryContextService.context(projectId, agentId, runId);
    }

    public String renderFrozenCanaryContext(
            String projectId,
            String agentId,
            List<Map<String, Object>> frozenRefs) {
        return canaryContextService.renderFrozen(
                projectId,
                agentId,
                frozenRefs);
    }

    @Scheduled(fixedDelayString =
            "${orbisops.skill-evolution.release.fixed-delay-ms:60000}")
    public void evaluateReleases() {
        releaseService.evaluateReleases();
    }

    void promoteIfReady(SkillReleaseSnapshot release) {
        releaseService.promoteIfReady(release);
    }

    void rollbackIfDegraded(SkillReleaseSnapshot release) {
        releaseService.rollbackIfDegraded(release);
    }

    private List<Map<String, Object>> mergeBaseArtifacts(
            String projectId,
            String skillId,
            int baseVersion,
            String baseSkillHash,
            List<Map<String, Object>> authored) {
        return packageAssembler.mergeBaseArtifacts(
                projectId,
                skillId,
                baseVersion,
                baseSkillHash,
                authored);
    }
}
