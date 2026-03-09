package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.analysis.AnalysisRunPort;
import cn.lgs.orbisops.trigger.application.ops.OpsAnalysisApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsAnalysisRunService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OpsAnalysisRunAdapter implements
        AnalysisRunPort<OpsAgentRunRequestDTO, OpsAgentRunRecordDTO, GraphEvent> {

    private final OpsAnalysisRunService runService;
    private final OpsAnalysisApplicationService analysisService;
    private final GraphEventApplicationService eventService;

    public OpsAnalysisRunAdapter(OpsAnalysisRunService runService,
                                 OpsAnalysisApplicationService analysisService,
                                 GraphEventApplicationService eventService) {
        this.runService = runService;
        this.analysisService = analysisService;
        this.eventService = eventService;
    }

    @Override
    public OpsAgentRunRecordDTO submit(OpsAgentRunRequestDTO request, String actor) {
        OpsAgentRunRequestDTO normalized = analysisService.normalizeRequest(request);
        normalized.setRequestedBy(actor);
        return runService.submit(normalized, analysisService::buildAnalysis);
    }

    @Override public Optional<OpsAgentRunRecordDTO> get(String runId) { return runService.get(runId); }
    @Override public List<OpsAgentRunRecordDTO> list(int limit) { return runService.list(limit); }
    @Override public boolean cancel(String runId) { return runService.cancel(runId); }
    @Override public int activeCount(String projectId) { return runService.activeCountByProject(projectId); }
    @Override public String id(OpsAgentRunRecordDTO run) { return run == null ? "" : run.getRunId(); }
    @Override public String status(OpsAgentRunRecordDTO run) { return run == null ? "" : run.getStatus(); }
    @Override public List<GraphEvent> events(String runId) { return eventService.list(runId); }
}
