package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.application.analysis.AnalysisTaskSupplementPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillRuntimeUsageRecorder;
import cn.lgs.orbisops.trigger.ops.toolset.OpsEvidenceStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OpsAnalysisTaskSupplementAdapter implements AnalysisTaskSupplementPort {

    private final GraphEventApplicationService graphEvents;
    private final OpsEvidenceStore evidenceStore;
    private final OpsToolResultStore toolResultStore;
    private final ChangePackageQueryService changePackages;
    private final OpsSkillRuntimeUsageRecorder skillUsages;

    public OpsAnalysisTaskSupplementAdapter(
            GraphEventApplicationService graphEvents,
            OpsEvidenceStore evidenceStore,
            OpsToolResultStore toolResultStore,
            ChangePackageQueryService changePackages,
            OpsSkillRuntimeUsageRecorder skillUsages) {
        this.graphEvents = graphEvents;
        this.evidenceStore = evidenceStore;
        this.toolResultStore = toolResultStore;
        this.changePackages = changePackages;
        this.skillUsages = skillUsages;
    }

    @Override
    public List<GraphEvent> events(String runId, int limit) {
        return graphEvents.list(runId, 0L, limit);
    }

    @Override
    public List<Map<String, Object>> evidence(String projectId, String runId, int limit) {
        return evidenceStore.listForRun(projectId, runId, limit);
    }

    @Override
    public List<Map<String, Object>> toolResults(String projectId, String runId, int limit) {
        return toolResultStore.listForRun(projectId, runId, limit);
    }

    @Override
    public List<Map<String, Object>> changePackages(String projectId, String sessionId, int limit) {
        return changePackages.list(new ChangePackageListQuery(
                Map.of("projectId", projectId, "sessionId", sessionId), limit));
    }

    @Override
    public List<Map<String, Object>> skillUsages(String projectId, String runId) {
        return skillUsages.listForRun(projectId, runId);
    }
}
