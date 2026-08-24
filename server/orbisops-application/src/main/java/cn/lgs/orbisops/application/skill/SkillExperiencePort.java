package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterEvidence;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;

import java.util.List;

/** Persistence port for Skill experience Episodes, Observations and Clusters. */
public interface SkillExperiencePort {

    void upsertEpisode(SkillExperienceObservation observation);

    boolean insertObservation(SkillExperienceObservation observation);

    /** Preserve an admitted observation's identity across retries, rule upgrades and semantic reassignment. */
    default java.util.Optional<String> recordedClusterKey(String projectId,String agentId,String observationId) {
        return java.util.Optional.empty();
    }

    default boolean recordVerifiedContribution(SkillExperienceObservation observation) { return false; }

    void upsertCluster(SkillExperienceObservation observation);

    SkillExperienceClusterSnapshot cluster(
            String projectId,
            String agentId,
            String clusterKey);

    SkillExperienceClusterEvidence clusterEvidence(
            String projectId,
            String agentId,
            String clusterKey);

    List<SkillExperienceConsolidationSample> consolidationSamples(
            String projectId,
            String agentId,
            String clusterKey,
            int limit);

    default List<SkillExperienceConsolidationSample> consolidationSamples(String projectId,String agentId,String clusterKey,int limit,String requiredRun) {
        return consolidationSamples(projectId,agentId,clusterKey,limit);
    }

    boolean markClusterPromoted(
            String projectId,
            String agentId,
            String clusterKey,
            String candidateId);

    void markObservationsPromoted(
            String projectId,
            String agentId,
            String clusterKey);
}
