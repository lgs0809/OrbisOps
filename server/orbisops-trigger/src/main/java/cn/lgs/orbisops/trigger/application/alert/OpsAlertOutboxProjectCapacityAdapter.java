package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.application.alert.AlertOutboxProjectCapacityPort;
import cn.lgs.orbisops.trigger.ops.OpsAnalysisRunService;
import org.springframework.stereotype.Component;

@Component
public class OpsAlertOutboxProjectCapacityAdapter implements AlertOutboxProjectCapacityPort {

    private final OpsAnalysisRunService analysisRuns;

    public OpsAlertOutboxProjectCapacityAdapter(OpsAnalysisRunService analysisRuns) {
        this.analysisRuns = analysisRuns;
    }

    @Override
    public boolean canDispatch(String projectId, int maxRunning) {
        return analysisRuns.activeCountByProject(projectId) < Math.max(1, maxRunning);
    }
}
