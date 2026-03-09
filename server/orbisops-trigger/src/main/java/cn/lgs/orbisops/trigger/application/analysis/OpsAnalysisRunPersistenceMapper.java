package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunSnapshot;
import cn.lgs.orbisops.trigger.ops.OpsJsonSnapshotCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpsAnalysisRunPersistenceMapper {

    public AnalysisRunSnapshot snapshot(OpsAgentRunRecordDTO run) {
        OpsAgentRunRequestDTO request = run == null ? null : run.getRequest();
        return new AnalysisRunSnapshot(
                run == null ? "" : run.getRunId(),
                request == null ? "" : text(request.getProjectId()),
                request == null ? "" : text(request.getTriggerSource()),
                run == null ? "" : run.getStatus(),
                OpsJsonSnapshotCodec.write(request),
                OpsJsonSnapshotCodec.write(run == null ? null : run.getResponse()),
                run == null ? null : run.getErrorMessage(),
                run == null ? "" : run.getCreatedAt(),
                run == null ? "" : run.getUpdatedAt(),
                run == null ? null : run.getDurationMs());
    }

    public OpsAgentRunRecordDTO record(AnalysisRunSnapshot snapshot) {
        if (snapshot == null) return null;
        return OpsAgentRunRecordDTO.builder()
                .runId(snapshot.runId())
                .status(snapshot.status())
                .request(readSnapshot(snapshot.runId(), "request_json", snapshot.requestJson(), OpsAgentRunRequestDTO.class))
                .response(readSnapshot(snapshot.runId(), "response_json", snapshot.responseJson(), OpsAnalysisResponseDTO.class))
                .errorMessage(snapshot.errorMessage())
                .createdAt(snapshot.createdAt())
                .updatedAt(snapshot.updatedAt())
                .durationMs(snapshot.durationMs())
                .build();
    }

    private <T> T readSnapshot(String runId, String field, String value, Class<T> type) {
        if (value == null) return null;
        try {
            return OpsJsonSnapshotCodec.read(value, type);
        } catch (Exception e) {
            log.warn("Agent 运行快照不可读，保留运行元数据，runId={} field={} error={}",
                    runId, field, e.getMessage());
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
