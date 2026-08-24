package cn.lgs.orbisops.trigger.ops.runtime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpsAgentChatRequest {

    private String userId;
    private String sessionId;
    private String runId;
    private String query;
    private String mode;
    private String engine;
    private String projectId;
    private String agentDefinitionId;
    private Integer agentVersion;
    private Boolean previewDraft;
    private OpsAgentDefinition agentDefinition;
    private String modelId;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private Boolean enableThinking;
    /** Optional per-request memory override. Null preserves the product mode default. */
    private Boolean memoryEnabled;
    /** Typed diagnosis controls shared with direct Ops Agent runs. */
    private Integer rangeMinutes;
    private String promWindow;
    private Boolean includeRecentLogs;
    private Integer maxRounds;
    private Integer subAgentMaxIterations;
    private Integer nodeTimeoutSeconds;
    private Integer maxEvidenceItems;
    private Boolean changeRequested;
    private Boolean notifyChannel;
    private String notificationChannelId;
    private String notificationTarget;

    /** Server-owned authority downgrade. Ignored from HTTP JSON so clients cannot self-assert it. */
    @JsonIgnore
    private Boolean trustedObserveOnly;

    /** Captured only after resolving the executable definition / persisted context bundle. */
    @JsonIgnore
    private OpsExplicitSkillBindings trustedSkillBindings;
    @JsonIgnore
    private OpsRuntimeSkillFrame trustedSkillFrame;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
