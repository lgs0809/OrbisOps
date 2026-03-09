package cn.lgs.orbisops.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 周期任务调度配置记录。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskScheduleRecord {

    private Long id;
    private String projectId;
    private String agentId;
    private String taskName;
    private String description;
    private String cronExpression;
    private String taskParam;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
