package cn.lgs.orbisops.trigger.ops.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Configurable operations agent definition inspired by the scaffold project.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpsAgentDefinition {

    private String agentId;
    private Integer schemaVersion;
    private Integer version;
    private String definitionHash;
    private String lifecycle;
    private String phase;
    private String name;
    private String projectId;
    private String engine;
    private String description;
    private String instruction;
    private String source;
    private String definitionKind;
    private String workflowInvocationMode;
    private Boolean workflowAutoSelectEnabled;
    private Integer workflowPriority;
    private String modelId;
    private String startNodeId;
    private Integer defaultMaxMainRounds;
    private Integer defaultSubAgentMaxIterations;
    private String agentScopeMode;
    private Integer agentScopeMaxConcurrency;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private Boolean queryRewriteEnabled;
    private Boolean changePackageEnabled;

    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Builder.Default
    private List<String> capabilities = new ArrayList<>();

    @Builder.Default
    private List<String> whenToUse = new ArrayList<>();

    @Builder.Default
    private List<String> whenNotToUse = new ArrayList<>();

    @Builder.Default
    private List<String> routingKeywords = new ArrayList<>();

    @Builder.Default
    private List<String> mcpIds = new ArrayList<>();

    @Builder.Default
    private List<String> executionTargetIds = new ArrayList<>();

    @Builder.Default
    private List<OpsWorkflowNode> nodes = new ArrayList<>();

    @Builder.Default
    private List<OpsGraphEdge> edges = new ArrayList<>();

    @Builder.Default
    private List<OpsLoopPolicy> loops = new ArrayList<>();

    @Builder.Default
    private List<OpsMcpServerConfig> mcpServers = new ArrayList<>();

    @Builder.Default
    private List<OpsAgentScopeConfig> agentscopeAgents = new ArrayList<>();

    public Optional<OpsWorkflowNode> node(String nodeId) {
        if (!StringUtils.hasText(nodeId) || nodes == null) {
            return Optional.empty();
        }
        return nodes.stream()
                .filter(node -> nodeId.equals(node.getNodeId()))
                .findFirst();
    }

}
