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
public class OpsAgentChatResponse {

    private String sessionId;
    private String userId;
    private String agentId;
    private Integer agentVersion;
    private String mode;
    private String engine;
    private String content;

    @Builder.Default
    private List<OpsRuntimeEvent> events = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
