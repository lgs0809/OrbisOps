package cn.lgs.orbisops.trigger.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsAlertTriggerEvent {

    private Long id;
    private Long ruleId;
    private String ruleName;
    private String projectId;
    private String sourceType;
    private String status;
    private String dedupKey;
    private String fingerprint;
    private String alertName;
    private String severity;
    private String serviceName;
    private String receiver;
    private String runId;
    private String runStatus;
    private String finalSummary;
    private String completedAt;
    private String errorMessage;
    private String labelsJson;
    private String annotationsJson;
    private String payloadJson;
    private String createTime;
}
