package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisRun;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** DTO anti-corruption mapper for asynchronous analysis run state. */
public final class OpsAsyncAnalysisRunProtocolMapper {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OpsAgentRunRecordDTO record(
            AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> run) {
        if (run == null) {
            return null;
        }
        return OpsAgentRunRecordDTO.builder()
                .runId(run.runId())
                .status(run.status() == null ? null : run.status().name())
                .request(run.request())
                .response(run.response())
                .errorMessage(run.errorMessage())
                .createdAt(format(run.createdAt()))
                .updatedAt(format(run.updatedAt()))
                .durationMs(run.durationMs())
                .build();
    }

    public AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> run(
            OpsAgentRunRecordDTO record) {
        if (record == null) {
            return null;
        }
        return new AsyncAnalysisRun<>(
                record.getRunId(),
                AnalysisRunStatus.require(record.getStatus()),
                record.getRequest(),
                record.getResponse(),
                record.getErrorMessage(),
                parse(record.getCreatedAt()),
                parse(record.getUpdatedAt()),
                record.getDurationMs());
    }

    private String format(LocalDateTime value) {
        return value == null ? null : FORMATTER.format(value);
    }

    private LocalDateTime parse(String value) {
        return value == null || value.trim().isBlank()
                ? null
                : LocalDateTime.parse(value.trim(), FORMATTER);
    }
}
