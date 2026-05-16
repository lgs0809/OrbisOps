package cn.lgs.orbisops.trigger.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsAlertTriggerRule {

    private Long id;
    private String ruleName;
    private Integer status;
    private String sourceType;
    private String alertNameRegex;
    private String severityRegex;
    private String serviceRegex;
    private String matchLabelsJson;
    private String notificationTarget;
    private String notificationChannelId;
    private String webhookSecret;
    private String projectId;
    private String agentDefinitionId;
    private String agentBindingMode;
    private Integer agentVersion;
    private String agentDefinitionHash;
    private String questionTemplate;
    private Integer rangeMinutes;
    private String promWindow;
    private Boolean includeRecentLogs;
    private Boolean notifyChannel;
    private Integer subAgentMaxIterations;
    private Integer nodeTimeoutSeconds;
    private Integer maxEvidenceItems;
    private Integer dedupWindowSeconds;
    private String createTime;
    private String updateTime;
}
