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
public class OpsChatSession {

    private String sessionId;
    private String userId;
    private String projectId;
    private String agentId;
    private String agentBindingMode;
    private Integer agentVersion;
    private String agentDefinitionHash;
    private String title;
    private String mode;
    private String engine;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private String status;
    private Long stateVersion;
    private Integer messageCount;
    private String lastMessage;
    private String createdAt;
    private String lastActiveAt;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
