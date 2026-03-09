package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运维 Agent 异步运行记录。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsAgentRunRecordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String runId;
    private String status;
    private OpsAgentRunRequestDTO request;
    private OpsAnalysisResponseDTO response;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;
    private Long durationMs;

}
