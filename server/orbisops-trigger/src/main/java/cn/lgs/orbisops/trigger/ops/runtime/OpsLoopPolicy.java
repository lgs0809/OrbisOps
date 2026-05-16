package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsLoopPolicy {

    private String loopId;
    private String name;

    @Builder.Default
    private List<String> nodes = new ArrayList<>();

    @Builder.Default
    private List<String> feedbackEdges = new ArrayList<>();

    private Integer maxRounds;
    private String stopCondition;
    private Integer timeoutSeconds;
    private String exitEdge;
    private String countMode;

    @Builder.Default
    private Map<String, Object> config = new LinkedHashMap<>();

}
