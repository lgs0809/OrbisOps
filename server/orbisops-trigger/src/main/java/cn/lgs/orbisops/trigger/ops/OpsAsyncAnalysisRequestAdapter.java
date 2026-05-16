package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisRequestPort;

/** Request protocol adapter for run identity and project ownership. */
public final class OpsAsyncAnalysisRequestAdapter implements AsyncAnalysisRequestPort<OpsAgentRunRequestDTO> {

    @Override
    public void bindRunId(OpsAgentRunRequestDTO request, String runId) {
        request.setRunId(runId);
    }

    @Override
    public String projectId(OpsAgentRunRequestDTO request) {
        return request == null ? "" : request.getProjectId();
    }
}
