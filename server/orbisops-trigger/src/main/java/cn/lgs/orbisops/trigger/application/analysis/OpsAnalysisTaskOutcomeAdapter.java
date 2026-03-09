package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.application.analysis.AnalysisTaskOutcomePort;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillRuntimeUsageRecorder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsAnalysisTaskOutcomeAdapter implements AnalysisTaskOutcomePort {

    private final OpsSkillRuntimeUsageRecorder skillUsages;

    public OpsAnalysisTaskOutcomeAdapter(OpsSkillRuntimeUsageRecorder skillUsages) {
        this.skillUsages = skillUsages;
    }

    @Override
    public void reconcile(
            String projectId,
            String runId,
            boolean negativeFeedback,
            boolean evidenceSufficient) {
        skillUsages.reconcileRunOutcome(projectId, runId, Map.of(
                "userNegativeFeedback", negativeFeedback,
                "evidenceSufficient", evidenceSufficient));
    }
}
