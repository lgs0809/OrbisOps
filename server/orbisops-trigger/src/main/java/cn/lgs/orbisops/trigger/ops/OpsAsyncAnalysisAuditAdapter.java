package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisAuditPort;
import cn.lgs.orbisops.application.audit.AnalysisAuditApplicationService;
import cn.lgs.orbisops.trigger.application.audit.OpsAnalysisAuditMapper;

/** Analysis audit adapter for asynchronous run terminal outcomes. */
public final class OpsAsyncAnalysisAuditAdapter implements
        AsyncAnalysisAuditPort<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> {

    private final AnalysisAuditApplicationService audits;
    private final OpsAnalysisAuditMapper mapper;

    public OpsAsyncAnalysisAuditAdapter(
            AnalysisAuditApplicationService audits,
            OpsAnalysisAuditMapper mapper) {
        if (audits == null) {
            throw new IllegalArgumentException("OPS_ANALYSIS_AUDIT_SERVICE_REQUIRED");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("OPS_ANALYSIS_AUDIT_MAPPER_REQUIRED");
        }
        this.audits = audits;
        this.mapper = mapper;
    }

    @Override
    public void succeeded(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            long durationMs) {
        audits.append(mapper.success(request, response, durationMs));
    }

    @Override
    public void failed(OpsAgentRunRequestDTO request, Exception error, long durationMs) {
        audits.append(mapper.failure(request, error, durationMs));
    }
}
