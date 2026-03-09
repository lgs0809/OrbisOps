package cn.lgs.orbisops.trigger.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsIncidentTimelineItem {

    private Long id;
    private String incidentId;
    private String eventType;
    private String title;
    private String detail;
    private String actor;
    private String refType;
    private String refId;
    private String payloadJson;
    private String createTime;

}
