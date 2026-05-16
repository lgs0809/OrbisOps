package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 周期任务执行记录响应 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskExecutionResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long scheduleId;
    private String taskName;
    private String agentId;
    private String triggerType;
    private String status;
    private String startedAt;
    private String endedAt;
    private String input;
    private String output;
    private String errorMessage;

}
