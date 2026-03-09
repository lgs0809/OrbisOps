package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Map;

/** Records the Skill versions actually used by one runtime run. */
public record SkillRuntimeUsageCommand(
        String projectId,
        String agentId,
        String runId,
        String contextBundleHash,
        List<SkillRuntimeUsageReference> references,
        Map<String, Object> outcome) {

    public SkillRuntimeUsageCommand {
        references = references == null ? List.of() : List.copyOf(references);
        outcome = outcome == null ? Map.of() : Map.copyOf(outcome);
    }
}
