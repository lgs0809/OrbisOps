package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 周期任务响应 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskScheduleResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String projectId;
    private String createdBy;
    private String executionType;
    private String agentId;
    private String agentBindingMode;
    private Integer agentVersion;
    private String agentDefinitionHash;
    private String taskName;
    private String description;
    private String cronExpression;
    private String taskParam;
    private Integer status;
    private Integer rangeMinutes;
    private String promWindow;
    private Boolean includeRecentLogs;
    private Integer maxRounds;
    private Integer subAgentMaxIterations;
    private Integer nodeTimeoutSeconds;
    private Integer maxEvidenceItems;
    private Boolean notifyChannel;
    private String notificationChannelId;
    private String notificationTarget;
    private Boolean lightweightScreeningEnabled;
    private String screeningSourceType;
    private String screeningPrimaryUri;
    private Double maxErrorRatePercent;
    private Double maxCpuPercent;
    private Double maxHeapPercent;
    private Double minInstanceUpRatio;
    private String createTime;
    private String updateTime;

}
