package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillRuntimeUsageApplicationService;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsageCommand;
import cn.lgs.orbisops.application.skill.SkillRuntimeUsageReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Trigger facade mapping runtime context references into the Skill usage use case. */
@Service
public class OpsSkillRuntimeUsageRecorder {

    private final SkillRuntimeUsageApplicationService applicationService;

    public OpsSkillRuntimeUsageRecorder(
            SkillRuntimeUsageApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public void record(
            String projectId,
            String agentId,
            String runId,
            String contextBundleHash,
            List<Map<String, Object>> refs,
            Map<String, Object> outcome) {
        List<SkillRuntimeUsageReference> references = refs == null
                ? List.of()
                : refs.stream()
                .filter(ref -> ref != null)
                .map(ref -> new SkillRuntimeUsageReference(
                        text(ref.get("skillId")),
                        number(ref.get("version")),
                        text(ref.get("skillHash")),
                        text(ref.get("usedAtNode"))))
                .toList();
        applicationService.record(new SkillRuntimeUsageCommand(
                projectId,
                agentId,
                runId,
                contextBundleHash,
                references,
                outcome));
    }

    public int reconcileRunOutcome(
            String projectId,
            String runId,
            Map<String, Object> facts) {
        return applicationService.reconcileRunOutcome(
                projectId,
                runId,
                facts);
    }

    public List<Map<String, Object>> listForRun(
            String projectId,
            String runId) {
        return applicationService.listForRun(projectId, runId);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int number(Object value) {
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
