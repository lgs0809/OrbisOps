package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Data
@Builder
public class OpsRuntimeResourceContext {

    private OpsAgentDefinition definition;
    private OpsWorkflowNode node;
    private OpsAgentScopeConfig agentScope;
    private OpsAgentChatRequest request;
    private AgentRunExecutionContext executionContext;
    private List<OpsRuntimeEvent> events;
    private Consumer<OpsRuntimeEvent> eventSink;
    private String projectId;
    private String modelId;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private Boolean repairEnabled;
    private Boolean changePackageEnabled;
    private String skillContext;
    private ChatModel chatModel;
    private java.util.function.BiFunction<OpsMcpServerConfig,String,Map<String,Object>> mcpDefinitionReader;
    private java.util.function.Function<OpsMcpServerConfig,List<Map<String,Object>>> mcpDefinitionsReader;

    @Builder.Default
    private Set<String> skillNames = new LinkedHashSet<>();

    @Builder.Default
    private Set<String> mcpIds = new LinkedHashSet<>();

    @Builder.Default
    private Set<String> executionTargetIds = new LinkedHashSet<>();

    @Builder.Default
    private List<OpsMcpServerConfig> mcpServers = new ArrayList<>();

    @Builder.Default
    private List<ToolCallback> tools = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void record(OpsRuntimeEvent event) {
        if (event == null) {
            return;
        }
        if (events != null) {
            events.add(event);
        }
        if (eventSink != null) {
            eventSink.accept(event);
        }
    }

    public String ownerLabel() {
        if (node != null && StringUtils.hasText(node.getNodeId())) {
            return "NODE:" + node.getNodeId();
        }
        if (agentScope != null && StringUtils.hasText(agentScope.getAgentId())) {
            return "AGENTSCOPE:" + agentScope.getAgentId();
        }
        return "AGENT:" + (definition == null ? "" : definition.getAgentId());
    }

    public OpsRuntimeResourceBundle toBundle() {
        return OpsRuntimeResourceBundle.builder()
                .definition(definition)
                .agentVersion(definition == null ? null : definition.getVersion())
                .chatModel(chatModel)
                .mcpDefinitionReader(mcpDefinitionReader)
                .mcpDefinitionsReader(mcpDefinitionsReader)
                .projectId(projectId)
                .ragEnabled(ragEnabled)
                .knowledgeBaseId(knowledgeBaseId)
                .modelId(modelId)
                .skillContext(skillContext)
                .skillNames(new ArrayList<>(skillNames))
                .mcpIds(new ArrayList<>(mcpIds))
                .mcpServers(mcpServers)
                .tools(tools)
                .metadata(metadata)
                .build();
    }

}
