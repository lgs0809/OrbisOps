package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsGraphEdge {

    private String edgeId;
    private String name;
    private String from;
    private String to;
    private String conditionType;
    private String condition;
    private String description;
    private Integer priority;
    private Boolean defaultEdge;
    private Boolean feedback;

    @Builder.Default
    private Map<String, Object> dataMapping = new LinkedHashMap<>();

}
