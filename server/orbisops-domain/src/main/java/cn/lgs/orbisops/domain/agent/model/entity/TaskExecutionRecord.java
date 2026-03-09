package cn.lgs.orbisops.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 周期任务执行记录。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskExecutionRecord {

    private Long id;
    private Long scheduleId;
    private String taskName;
    private String agentId;
    private String triggerType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String input;
    private String output;
    private String errorMessage;
}
