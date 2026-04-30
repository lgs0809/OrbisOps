package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillExperienceAuditPort;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Trigger audit adapter for newly admitted Skill experience observations. */
@Component
public class OpsSkillExperienceAuditAdapter
        implements SkillExperienceAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsSkillExperienceAuditAdapter(
            OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void recordObservation(SkillExperienceObservation observation) {
        auditService.recordRuntimeEvent(
                observation.projectId(),
                observation.agentId(),
                "",
                "skill-experience",
                "SKILL_OBSERVATION_RECORDED",
                observation.observationId(),
                "LOW",
                "OBSERVED",
                Map.of(
                        "runId",
                        observation.runId(),
                        "episodeId",
                        observation.episodeId(),
                        "clusterKey",
                        observation.clusterKey(),
                        "taskTemplateHash",
                        observation.taskTemplateHash(),
                        "trajectoryHash",
                        observation.trajectoryHash()));
    }
}
