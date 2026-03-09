package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运维 Agent 异步运行请求。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsAgentRunRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String runId;
    private String requestedBy;
    private String projectId;
    private String agentDefinitionId;
    private Integer agentVersion;
    private String agentDefinitionSnapshotJson;
    private String query;
    private String question;
    private Integer rangeMinutes;
    private String promWindow;
    private Boolean includeRecentLogs;
    private Integer maxRounds;
    private Integer subAgentMaxIterations;
    private Integer nodeTimeoutSeconds;
    private Integer maxEvidenceItems;
    /** Explicitly request governed ChangePackage preparation after evidence collection. */
    private Boolean changeRequested;
    private Boolean notifyChannel;
    private String notificationChannelId;
    private String notificationTarget;
    private String triggerSource;
    /** REACT for default/platform Agent, WORKFLOW for a user-authored drag/drop fixed graph. */
    private String executionStyle;
    private String triggerEventId;

}
