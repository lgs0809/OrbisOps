package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsGraphEdge;
import cn.lgs.orbisops.trigger.ops.runtime.OpsLoopPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stable protocol projection of Agent Definition runtime DTOs. */
public final class OpsAgentDefinitionViewMapper {

    private final OpsAgentDefinitionExecutionShapeMapper executionShapeMapper;

    public OpsAgentDefinitionViewMapper() {
        this(new OpsAgentDefinitionExecutionShapeMapper());
    }

    OpsAgentDefinitionViewMapper(
            OpsAgentDefinitionExecutionShapeMapper executionShapeMapper) {
        this.executionShapeMapper = executionShapeMapper == null
                ? new OpsAgentDefinitionExecutionShapeMapper()
                : executionShapeMapper;
    }

    public Map<String, Object> view(OpsAgentDefinition definition) {
        if (definition == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentId", definition.getAgentId());
        data.put("schemaVersion", definition.getSchemaVersion());
        data.put("version", definition.getVersion());
        data.put("definitionHash", definition.getDefinitionHash());
        data.put("lifecycle", definition.getLifecycle());
        data.put("name", definition.getName());
        data.put("projectId", definition.getProjectId());
        data.put("scopeType", definition.getProjectId() == null
                || definition.getProjectId().isBlank()
                ? "PLATFORM_TEMPLATE"
                : "PROJECT");
        data.put("engine", executionShapeMapper.inferEngine(definition));
        data.put("description", definition.getDescription());
        data.put("instruction", definition.getInstruction());
        data.put("definitionKind", definition.getDefinitionKind());
        data.put("workflowInvocationMode", definition.getWorkflowInvocationMode());
        data.put("workflowAutoSelectEnabled", definition.getWorkflowAutoSelectEnabled());
        data.put("workflowPriority", definition.getWorkflowPriority());
        data.put("whenToUse", definition.getWhenToUse());
        data.put("whenNotToUse", definition.getWhenNotToUse());
        data.put("routingKeywords", definition.getRoutingKeywords());
        data.put("modelId", definition.getModelId());
        data.put("startNodeId", definition.getStartNodeId());
        data.put("defaultMaxMainRounds", definition.getDefaultMaxMainRounds());
        data.put("defaultSubAgentMaxIterations", definition.getDefaultSubAgentMaxIterations());
        data.put("ragEnabled", definition.getRagEnabled());
        data.put("knowledgeBaseId", definition.getKnowledgeBaseId());
        data.put("queryRewriteEnabled", definition.getQueryRewriteEnabled());
        data.put("changePackageEnabled", definition.getChangePackageEnabled());
        data.put("skills", definition.getSkills());
        data.put("mcpIds", definition.getMcpIds());
        data.put("executionTargetIds", definition.getExecutionTargetIds());
        data.put("mcpServers", definition.getMcpServers());
        data.put("nodes", Optional.ofNullable(definition.getNodes()).orElse(List.of()).stream()
                .map(this::workflowNodeView)
                .toList());
        data.put("edges", Optional.ofNullable(definition.getEdges()).orElse(List.of()).stream()
                .map(this::graphEdgeView)
                .toList());
        data.put("loops", Optional.ofNullable(definition.getLoops()).orElse(List.of()).stream()
                .map(this::loopPolicyView)
                .toList());
        return data;
    }

    private Map<String, Object> graphEdgeView(OpsGraphEdge edge) {
        if (edge == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("edgeId", edge.getEdgeId());
        data.put("name", edge.getName());
        data.put("from", edge.getFrom());
        data.put("to", edge.getTo());
        data.put("conditionType", edge.getConditionType());
        data.put("condition", edge.getCondition());
        data.put("description", edge.getDescription());
        data.put("priority", edge.getPriority());
        data.put("defaultEdge", edge.getDefaultEdge());
        data.put("feedback", edge.getFeedback());
        data.put("dataMapping", edge.getDataMapping());
        return data;
    }

    private Map<String, Object> loopPolicyView(OpsLoopPolicy loop) {
        if (loop == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("loopId", loop.getLoopId());
        data.put("name", loop.getName());
        data.put("nodes", loop.getNodes());
        data.put("feedbackEdges", loop.getFeedbackEdges());
        data.put("maxRounds", loop.getMaxRounds());
        data.put("stopCondition", loop.getStopCondition());
        data.put("timeoutSeconds", loop.getTimeoutSeconds());
        data.put("exitEdge", loop.getExitEdge());
        data.put("countMode", loop.getCountMode());
        data.put("config", loop.getConfig());
        return data;
    }

    private Map<String, Object> workflowNodeView(OpsWorkflowNode node) {
        if (node == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodeId", node.getNodeId());
        data.put("type", node.getType());
        data.put("mode", node.getMode());
        data.put("agent", node.getAgent());
        data.put("description", node.getDescription());
        data.put("instruction", node.getInstruction());
        data.put("modelId", node.getModelId());
        data.put("subEngine", node.getSubEngine());
        data.put("outputKey", node.getOutputKey());
        data.put("ragEnabled", node.getRagEnabled());
        data.put("knowledgeBaseId", node.getKnowledgeBaseId());
        data.put("repairEnabled", node.getRepairEnabled());
        data.put("changePackageEnabled", node.getChangePackageEnabled());
        data.put("skills", node.getSkills());
        data.put("mcpIds", node.getMcpIds());
        data.put("executionTargetIds", node.getExecutionTargetIds());
        data.put("mcpServers", node.getMcpServers());
        data.put("config", node.getConfig());
        return data;
    }
}
