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
