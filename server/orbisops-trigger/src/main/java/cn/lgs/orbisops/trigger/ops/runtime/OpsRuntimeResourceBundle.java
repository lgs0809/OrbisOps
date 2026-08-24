package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsRuntimeResourceBundle {

    private OpsAgentDefinition definition;
    private Integer agentVersion;
    private ChatModel chatModel;
    private java.util.function.BiFunction<OpsMcpServerConfig,String,Map<String,Object>> mcpDefinitionReader;
    private java.util.function.Function<OpsMcpServerConfig,List<Map<String,Object>>> mcpDefinitionsReader;
    private String projectId;
    private Boolean ragEnabled;
    private String knowledgeBaseId;
    private String modelId;
    private String skillContext;

    @Builder.Default
    private List<String> skillNames = new ArrayList<>();

    @Builder.Default
    private List<String> mcpIds = new ArrayList<>();

    @Builder.Default
    private List<OpsMcpServerConfig> mcpServers = new ArrayList<>();

    @Builder.Default
    private List<ToolCallback> tools = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
