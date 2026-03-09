package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSourceLoader;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsGraphEdge;
import cn.lgs.orbisops.trigger.ops.runtime.OpsLoopPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Trigger adapter that discovers and maps legacy YAML resources into Agent Definitions. */
@Slf4j
@Component
public class OpsAgentDefinitionYamlLoader implements AgentDefinitionSourceLoader<OpsAgentDefinition> {

    private final ResourcePatternResolver resourcePatternResolver;

    public OpsAgentDefinitionYamlLoader() {
        this(new PathMatchingResourcePatternResolver());
    }

    OpsAgentDefinitionYamlLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public List<OpsAgentDefinition> load(String locations) {
        List<OpsAgentDefinition> definitions = new ArrayList<>();
        for (String location : parseLocations(locations)) {
            try {
                Resource[] resources = resourcePatternResolver.getResources(location);
                for (Resource resource : resources) {
                    OpsAgentDefinition definition = parse(resource);
                    if (definition != null && StringUtils.hasText(definition.getAgentId())) {
                        definitions.add(definition);
                    }
                }
            } catch (Exception error) {
                log.warn("加载运维 Agent YAML 失败，location={}", location, error);
            }
        }
        return List.copyOf(definitions);
    }

    @SuppressWarnings("unchecked")
    OpsAgentDefinition parse(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            Object loaded = new Yaml().load(inputStream);
            if (!(loaded instanceof Map<?, ?> root)) {
                return null;
            }
            Map<String, Object> agent = map(root.get("agent"));
            if (agent.isEmpty()) {
                agent = map(root);
            }
            Map<String, Object> graph = map(agent.get("graph"));
            OpsAgentDefinition definition = OpsAgentDefinition.builder()
                    .agentId(text(agent, "agentId", null))
                    .version(integer(agent.get("version")))
                    .lifecycle(text(agent, "lifecycle", "PUBLISHED"))
                    .name(text(agent, "name", null))
                    .projectId(text(agent, "projectId", null))
                    .engine(text(agent, "engine", "CONTROLLED_GRAPH"))
                    .description(text(agent, "description", ""))
                    .instruction(text(agent, "instruction", ""))
                    .source(text(agent, "source", "YAML"))
                    .definitionKind(text(agent, "definitionKind", null))
                    .workflowInvocationMode(text(
                            agent, "workflowInvocationMode", null))
                    .workflowAutoSelectEnabled(bool(
                            agent.get("workflowAutoSelectEnabled")))
                    .workflowPriority(integer(agent.get("workflowPriority")))
                    .modelId(text(agent, "modelId", null))
                    .startNodeId(text(graph, "start", null))
                    .defaultMaxMainRounds(integer(agent.get("defaultMaxMainRounds")))
                    .defaultSubAgentMaxIterations(integer(agent.get("defaultSubAgentMaxIterations")))
                    .agentScopeMode(text(agent, "agentScopeMode", null))
                    .agentScopeMaxConcurrency(integer(agent.get("agentScopeMaxConcurrency")))
                    .ragEnabled(bool(agent.get("ragEnabled")))
                    .knowledgeBaseId(text(agent, "knowledgeBaseId", null))
                    .queryRewriteEnabled(bool(agent.get("queryRewriteEnabled")))
                    .changePackageEnabled(bool(agent.get("changePackageEnabled")))
                    .skills(stringList(agent.get("skills")))
                    .whenToUse(stringList(agent.get("whenToUse")))
                    .whenNotToUse(stringList(agent.get("whenNotToUse")))
                    .routingKeywords(stringList(agent.get("routingKeywords")))
                    .mcpIds(stringList(agent.get("mcpIds")))
                    .executionTargetIds(stringList(agent.get("executionTargetIds")))
                    .mcpServers(parseMcpServers(agent.get("mcpServers")))
                    .agentscopeAgents(parseAgentScopeAgents(agent.get("agentscopeAgents")))
                    .nodes(parseNodes(graph.get("nodes")))
                    .edges(parseEdges(graph.get("edges")))
                    .loops(parseLoops(graph.get("loops")))
                    .build();
            log.info("加载运维 Agent YAML 成功，agentId={}，resource={}",
                    definition.getAgentId(), resource.getFilename());
            return definition;
        } catch (Exception error) {
            log.warn("解析运维 Agent YAML 失败，resource={}", resource.getFilename(), error);
            return null;
        }
    }

    private List<OpsWorkflowNode> parseNodes(Object value) {
        List<OpsWorkflowNode> nodes = new ArrayList<>();
        for (Object item : list(value)) {
            Map<String, Object> values = map(item);
            String nodeId = text(values, "id", text(values, "nodeId", null));
            if (!StringUtils.hasText(nodeId)) {
                continue;
            }
            nodes.add(OpsWorkflowNode.builder()
                    .nodeId(nodeId)
                    .type(text(values, "type", "CUSTOM"))
                    .mode(text(values, "mode", text(map(values.get("config")), "mode", null)))
                    .agent(text(values, "agent", ""))
                    .description(text(values, "description", ""))
                    .instruction(text(values, "instruction", ""))
                    .modelId(text(values, "modelId", null))
                    .subEngine(text(values, "subEngine", ""))
                    .outputKey(text(values, "outputKey", ""))
                    .ragEnabled(bool(values.get("ragEnabled")))
                    .knowledgeBaseId(text(values, "knowledgeBaseId", null))
                    .repairEnabled(bool(values.get("repairEnabled")))
                    .changePackageEnabled(bool(values.get("changePackageEnabled")))
                    .skills(stringList(values.get("skills")))
                    .mcpIds(stringList(values.get("mcpIds")))
                    .executionTargetIds(stringList(values.get("executionTargetIds")))
                    .mcpServers(parseMcpServers(values.get("mcpServers")))
                    .config(map(values.get("config")))
                    .build());
        }
        return nodes;
    }

    private List<OpsAgentScopeConfig> parseAgentScopeAgents(Object value) {
        List<OpsAgentScopeConfig> agents = new ArrayList<>();
        int index = 0;
        for (Object item : list(value)) {
            Map<String, Object> values = map(item);
            String agentId = text(values, "agentId", text(values, "id", null));
            String name = text(values, "name", agentId);
            String instruction = text(values, "instruction", "");
            if (!StringUtils.hasText(agentId) && !StringUtils.hasText(instruction)) {
                continue;
            }
            agents.add(OpsAgentScopeConfig.builder()
                    .agentId(agentId)
                    .name(name)
                    .instruction(instruction)
                    .modelId(text(values, "modelId", null))
                    .outputKey(text(values, "outputKey", "agent_" + index))
                    .ragEnabled(bool(values.get("ragEnabled")))
                    .knowledgeBaseId(text(values, "knowledgeBaseId", null))
                    .maxIterations(integer(values.get("maxIterations")))
                    .maxDepth(integer(values.get("maxDepth")))
                    .role(text(values, "role", null))
                    .inheritProjectCapabilities(bool(values.get("inheritProjectCapabilities")))
                    .repairEnabled(bool(values.get("repairEnabled")))
                    .changePackageEnabled(bool(values.get("changePackageEnabled")))
                    .mcpServers(parseMcpServers(values.get("mcpServers")))
                    .mcpIds(stringList(values.get("mcpIds")))
                    .skills(stringList(values.get("skills")))
                    .executionTargetIds(stringList(values.get("executionTargetIds")))
                    .allowedToolNames(stringList(values.get("allowedToolNames")))
                    .build());
            index++;
        }
        return agents;
    }

    private List<OpsMcpServerConfig> parseMcpServers(Object value) {
        List<OpsMcpServerConfig> servers = new ArrayList<>();
        for (Object item : list(value)) {
            Map<String, Object> values = map(item);
            String name = text(values, "name", null);
            if (!StringUtils.hasText(name)) {
                continue;
            }
            servers.add(OpsMcpServerConfig.builder()
                    .name(name)
                    .description(text(values, "description", ""))
                    .transport(text(values, "transport", "stdio"))
                    .command(text(values, "command", ""))
                    .url(text(values, "url", ""))
                    .timeoutSeconds(integer(values.get("timeoutSeconds")))
                    .args(stringList(values.get("args")))
                    .env(stringMap(values.get("env")))
                    .headers(stringMap(values.get("headers")))
                    .toolCapabilities(stringMap(values.get("toolCapabilities")))
                    .allowedTools(stringList(values.get("allowedTools")))
                    .notificationTools(stringList(values.get("notificationTools")))
                    .blockedTools(stringList(values.get("blockedTools")))
                    .build());
        }
        return servers;
    }

    private List<OpsGraphEdge> parseEdges(Object value) {
        List<OpsGraphEdge> edges = new ArrayList<>();
        for (Object item : list(value)) {
            Map<String, Object> values = map(item);
            String from = text(values, "from", null);
            String to = text(values, "to", null);
            if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
                continue;
            }
            edges.add(OpsGraphEdge.builder()
                    .edgeId(text(values, "edgeId", text(values, "id", null)))
                    .name(text(values, "name", ""))
                    .from(from)
                    .to(to)
                    .conditionType(text(values, "conditionType", "always"))
                    .condition(text(values, "condition", "always"))
                    .description(text(values, "description", ""))
                    .priority(integer(values.get("priority")))
                    .defaultEdge(bool(values.get("defaultEdge")))
                    .feedback(bool(values.get("feedback")))
                    .dataMapping(map(values.get("dataMapping")))
                    .build());
        }
        return edges;
    }

    private List<OpsLoopPolicy> parseLoops(Object value) {
        List<OpsLoopPolicy> loops = new ArrayList<>();
        for (Object item : list(value)) {
            Map<String, Object> values = map(item);
            String loopId = text(values, "loopId", text(values, "id", null));
            if (!StringUtils.hasText(loopId)) {
                continue;
            }
            loops.add(OpsLoopPolicy.builder()
                    .loopId(loopId)
                    .name(text(values, "name", loopId))
                    .nodes(stringList(values.get("nodes")))
                    .feedbackEdges(stringList(values.get("feedbackEdges")))
                    .maxRounds(integer(values.get("maxRounds")))
                    .stopCondition(text(values, "stopCondition", ""))
                    .timeoutSeconds(integer(values.get("timeoutSeconds")))
                    .exitEdge(text(values, "exitEdge", ""))
                    .countMode(text(values, "countMode", "router_choice"))
                    .config(map(values.get("config")))
                    .build());
        }
        return loops;
    }

    private List<String> parseLocations(String locations) {
        if (!StringUtils.hasText(locations)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String location : locations.split(",")) {
            String trimmed = location.trim();
            if (StringUtils.hasText(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<?> list(Object value) {
        return value instanceof List<?> source ? source : List.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> source) {
            return source.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text);
        }
        return List.of();
    }

    private String text(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? defaultValue
                : String.valueOf(value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return null;
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> source) {
            source.forEach((key, item) -> {
                if (key != null && item != null) {
                    result.put(String.valueOf(key), String.valueOf(item));
                }
            });
        }
        return result;
    }
}
