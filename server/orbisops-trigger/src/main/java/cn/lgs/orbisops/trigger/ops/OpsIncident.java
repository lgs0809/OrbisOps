package cn.lgs.orbisops.trigger.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsIncident {

    private Long id;
    private String incidentId;
    private String projectId;
    private String title;
    private String status;
    private String severity;
    private String serviceName;
    private String sourceType;
    private String fingerprint;
    private String dedupKey;
    private String currentRunId;
    private String ownerUserId;
    private String summary;
    private String labelsJson;
    private String metadataJson;
    private Long occurrenceCount;
    private String affectedResourcesJson;
    private String firstSeenAt;
    private String lastSeenAt;
    private String createTime;
    private String updateTime;
    private String acknowledgedAt;
    private String resolvedAt;
    private String reviewedAt;

}
