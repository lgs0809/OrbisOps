package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionSignalApplicationService;
import cn.lgs.orbisops.trigger.application.skill.OpsSkillEvolutionSignalMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Legacy Skill Evolution signal facade limited to protocol mapping and application delegation. */
@Service
public class OpsSkillEvolutionSignalService {

    private final SkillEvolutionSignalApplicationService applicationService;
    private final OpsSkillEvolutionSignalMapper mapper;

    public OpsSkillEvolutionSignalService(
            SkillEvolutionSignalApplicationService applicationService,
            OpsSkillEvolutionSignalMapper mapper) {
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    public Map<String, Object> record(
            String signalType,
            String projectId,
            String agentId,
            String runId,
            String sessionId,
            Map<String, Object> payload) {
        return mapper.signalView(applicationService.record(mapper.signalCommand(
                signalType,
                projectId,
                agentId,
                runId,
                sessionId,
                payload)));
    }

    public Map<String, Object> createHint(
            String signalId,
            String projectId,
            String runId,
            String hintType,
            Map<String, Object> content) {
        return mapper.hintView(applicationService.createHint(mapper.hintCommand(
                signalId,
                projectId,
                runId,
                hintType,
                content)));
    }

    public List<Map<String, Object>> pendingHints(String projectId, int limit) {
        return mapper.pendingHintViews(applicationService.pendingHints(projectId, limit));
    }

    public void markHintsConsumed(List<String> hintIds, String candidateId) {
        applicationService.markHintsConsumed(hintIds, candidateId);
    }
}
