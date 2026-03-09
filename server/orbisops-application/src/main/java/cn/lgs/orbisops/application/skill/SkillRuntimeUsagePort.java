package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Map;

/** Persistence port for runtime Skill usage and outcome reconciliation. */
public interface SkillRuntimeUsagePort {

    boolean insert(SkillRuntimeUsageCommand command, SkillRuntimeUsageReference reference);

    List<SkillRuntimeUsageRecord> lockForRun(String projectId, String runId);

    boolean compareAndSetOutcome(
            long id,
            String persistenceToken,
            Map<String, Object> outcome);

    List<Map<String, Object>> listForRun(String projectId, String runId);
}
