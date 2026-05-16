package cn.lgs.orbisops.trigger.ops.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpsAgentScopeConfig {

    private String agentId;
    private String name;
    private String instruction;
    private String modelId;
    private String outputKey;
    private Map<?, ?> outputContract;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private Integer maxIterations;
    private Integer maxDepth;
    private String role;
    private Boolean inheritProjectCapabilities;
    private Boolean repairEnabled;
    private Boolean changePackageEnabled;
    private Boolean changePackageStatusEnabled;

    @Builder.Default
    private List<OpsMcpServerConfig> mcpServers = new ArrayList<>();

    @Builder.Default
    private List<String> mcpIds = new ArrayList<>();

    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Builder.Default
    private List<String> executionTargetIds = new ArrayList<>();

    @Builder.Default
    private List<String> allowedToolNames = new ArrayList<>();

}
