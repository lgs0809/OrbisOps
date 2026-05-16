package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisRunProcessManager;
import cn.lgs.orbisops.trigger.application.analysis.OpsAsyncAnalysisRunProtocolMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** DTO facade for the asynchronous analysis run Application process manager. */
@Service
public class OpsAnalysisRunService {

    public static final String STATUS_PENDING = OpsAnalysisRunStatus.PENDING;
    public static final String STATUS_RUNNING = OpsAnalysisRunStatus.RUNNING;
    public static final String STATUS_SUCCEEDED = OpsAnalysisRunStatus.SUCCEEDED;
    public static final String STATUS_FAILED = OpsAnalysisRunStatus.FAILED;
    public static final String STATUS_CANCELED = OpsAnalysisRunStatus.CANCELED;

    private final AsyncAnalysisRunProcessManager<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> processManager;
    private final OpsAsyncAnalysisRunProtocolMapper protocolMapper;

    public OpsAnalysisRunService(
            AsyncAnalysisRunProcessManager<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> processManager,
            OpsAsyncAnalysisRunProtocolMapper protocolMapper) {
        if (processManager == null) {
            throw new IllegalArgumentException("ASYNC_ANALYSIS_RUN_PROCESS_MANAGER_REQUIRED");
        }
        if (protocolMapper == null) {
            throw new IllegalArgumentException("OPS_ANALYSIS_RUN_PROTOCOL_MAPPER_REQUIRED");
        }
        this.processManager = processManager;
        this.protocolMapper = protocolMapper;
    }

    public OpsAgentRunRecordDTO submit(
            OpsAgentRunRequestDTO request,
            Function<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> analysisFunction) {
        return protocolMapper.record(processManager.submit(request, analysisFunction));
    }

    public Optional<OpsAgentRunRecordDTO> get(String runId) {
        return processManager.get(runId).map(protocolMapper::record);
    }

    public List<OpsAgentRunRecordDTO> list(int limit) {
        return processManager.list(limit).stream().map(protocolMapper::record).toList();
    }

    public int activeCountByProject(String projectId) {
        return processManager.activeCountByProject(projectId);
    }

    public boolean cancel(String runId) {
        return processManager.cancel(runId);
    }
}
