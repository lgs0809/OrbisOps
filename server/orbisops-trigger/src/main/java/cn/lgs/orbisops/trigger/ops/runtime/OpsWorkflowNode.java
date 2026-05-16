package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.graph.model.GraphEventNodeDescriptor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpsWorkflowNode implements GraphEventNodeDescriptor {

    private String nodeId;
    private String type;
    private String mode;
    private String agent;
    private String description;
    private String instruction;
    private String modelId;
    private String subEngine;
    private String outputKey;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private Boolean repairEnabled;
    private Boolean changePackageEnabled;

    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Builder.Default
    private List<String> mcpIds = new ArrayList<>();

    @Builder.Default
    private List<String> executionTargetIds = new ArrayList<>();

    @Builder.Default
    private List<OpsMcpServerConfig> mcpServers = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> config = new LinkedHashMap<>();

    @Override
    public String graphNodeId() {
        return nodeId;
    }

    @Override
    public String graphNodeType() {
        return type;
    }

    @Override
    public String graphAgent() {
        return agent;
    }

}
