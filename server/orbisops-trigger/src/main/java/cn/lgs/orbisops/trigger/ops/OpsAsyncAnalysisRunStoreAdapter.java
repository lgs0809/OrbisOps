package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisRun;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisRunStorePort;
import cn.lgs.orbisops.trigger.application.analysis.OpsAsyncAnalysisRunProtocolMapper;

import java.util.List;
import java.util.Optional;

/** Typed Application store adapter backed by the existing persistent/fallback run store. */
public final class OpsAsyncAnalysisRunStoreAdapter implements
        AsyncAnalysisRunStorePort<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> {

    private final OpsAnalysisRunStore store;
    private final OpsAsyncAnalysisRunProtocolMapper mapper;

    public OpsAsyncAnalysisRunStoreAdapter(
            OpsAnalysisRunStore store,
            OpsAsyncAnalysisRunProtocolMapper mapper) {
        if (store == null) {
            throw new IllegalArgumentException("OPS_ANALYSIS_RUN_STORE_REQUIRED");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("OPS_ANALYSIS_RUN_PROTOCOL_MAPPER_REQUIRED");
        }
        this.store = store;
        this.mapper = mapper;
    }

    @Override
    public void save(AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> run) {
        store.save(mapper.record(run));
    }

    @Override
    public Optional<AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO>> get(String runId) {
        return store.get(runId).map(mapper::run);
    }

    @Override
    public List<AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO>> list(int limit) {
        List<OpsAgentRunRecordDTO> values = store.list(limit);
        return values == null || values.isEmpty()
                ? List.of()
                : values.stream().map(mapper::run).toList();
    }

    @Override
    public int activeCountByProject(String projectId) {
        return store.activeCountByProject(projectId);
    }
}
