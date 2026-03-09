package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillPatchCandidateApplicationService;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Trigger compatibility facade for Skill patch candidate use cases. */
@Service
public class OpsSkillPatchCandidateService {

    private final SkillPatchCandidateApplicationService applicationService;

    public OpsSkillPatchCandidateService(
            SkillPatchCandidateApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public Map<String, Object> create(Map<String, Object> request) {
        return applicationService.create(request);
    }

    public Map<String, Object> get(String candidateId) {
        return applicationService.get(candidateId);
    }

    public void transition(
            String candidateId,
            String from,
            String to,
            String reason) {
        applicationService.transition(candidateId, from, to, reason);
    }

    public void updateStatus(
            String candidateId,
            String status,
            String reasonCode) {
        applicationService.updateStatus(candidateId, status, reasonCode);
    }
}
