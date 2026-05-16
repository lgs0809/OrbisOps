package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson2.JSON;
import cn.lgs.orbisops.domain.agentdefinition.service.DirectActionDataPolicy;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes deterministic Agent actions through the already-governed runtime tool bundle. */
final class OpsGraphDirectNodeExecutor {

    private final OpsRuntimeResourceAssembler resourceAssembler;

    OpsGraphDirectNodeExecutor(OpsRuntimeResourceAssembler resourceAssembler) {
        if (resourceAssembler == null) {
            throw new IllegalArgumentException("DIRECT_NODE_RESOURCE_ASSEMBLER_REQUIRED");
        }
        this.resourceAssembler = resourceAssembler;
    }

    OpsGraphNodeExecutionResult execute(OpsGraphNodeExecutionContext context) {
        List<Map<String, Object>> actions = actions(context.node());
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("DIRECT_NODE_ACTIONS_REQUIRED");
        }
        var dataPolicy = new DirectActionDataPolicy();
        actions.forEach(dataPolicy::validate);
        Map<String, Object> input = actions.stream().anyMatch(dataPolicy::needsInput)
                ? dataPolicy.parseObject(context.request().getQuery()) : Map.of();
        Map<String, Object> workflowData = new LinkedHashMap<>();
        if (context.state() != null) context.state().data().forEach((key, value) -> {
            if (key.startsWith("workflowData_")) workflowData.put(key, value);
        });
        OpsRuntimeResourceBundle bundle = resourceAssembler.assembleNode(
                context.definition(),
                context.node(),
                context.request(),
                context.events(),
                context.eventSink());
        Map<String, ToolCallback> tools = toolsByName(bundle == null ? null : bundle.getTools());
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> observations = new ArrayList<>();
        String lastOutput = "";
        int index = 0;
        for (Map<String, Object> action : actions) {
            index++;
            Object arguments = dataPolicy.arguments(action, input, workflowData);
            ResolvedAction resolved = resolveAction(action, arguments, tools, index);
            lastOutput = text(resolved.tool().call(resolved.input()));
            Map<String,Object> structured = action.containsKey("structuredOutputKey") ? dataPolicy.parseObject(lastOutput) : Map.of();
            if ("MCP_EVIDENCE_REFERENCE".equals(action.get("outputMode"))) {
                var projected = new OpsDirectMcpEvidenceProjection().project(structured,resolved.mcpId(),resolved.toolName());
                structured = projected.structured();
                lastOutput = projected.display();
            }

            Map<String, Object> observation = new LinkedHashMap<>();
            observation.put("index", index);
            observation.put("toolName", resolved.toolName());
            if (StringUtils.hasText(resolved.mcpId())) {
                observation.put("mcpId", resolved.mcpId());
                observation.put("callbackName", resolved.callbackName());
            }
            observation.put("output", lastOutput);
            String actionOutputKey = text(action.get("outputKey"));
            if (StringUtils.hasText(actionOutputKey)) {
                result.put(actionOutputKey, lastOutput);
                observation.put("outputKey", actionOutputKey);
            }
            observations.add(Map.copyOf(observation));
            if (action.get("structuredOutputKey") instanceof String key) {
                workflowData.put("workflowData_" + key, structured);
                result.put("workflowData_" + key, structured);
            }
        }
        result.put("directActions", List.copyOf(observations));
        return new OpsGraphNodeExecutionResult(lastOutput, result);
    }

    private ResolvedAction resolveAction(
            Map<String, Object> action,
            Object arguments,
            Map<String, ToolCallback> tools,
            int index) {
        String mcpId = text(action.get("mcpId"));
        String remoteToolName = text(action.get("remoteToolName"));
        if (StringUtils.hasText(mcpId) || StringUtils.hasText(remoteToolName)) {
            if (!StringUtils.hasText(mcpId) || !StringUtils.hasText(remoteToolName)) {
                throw new IllegalArgumentException("DIRECT_NODE_MCP_ACTION_REQUIRED:" + index);
            }
            String dispatcherName = OpsProgressiveMcpCallbackAdapter.safeToolCallbackName(
                    "project_mcp_" + mcpId);
            ToolCallback dispatcher = tools.get(dispatcherName);
            if (dispatcher != null) {
                Map<String, Object> routedInput = new LinkedHashMap<>();
                routedInput.put("toolName", remoteToolName);
                routedInput.put("arguments", arguments instanceof Map<?, ?> ? arguments : Map.of());
                return new ResolvedAction(
                        dispatcher,
                        remoteToolName,
                        mcpId,
                        dispatcherName,
                        JSON.toJSONString(routedInput));
            }
            ToolCallback direct = tools.get(remoteToolName);
            if (direct != null) {
                String input = arguments instanceof String raw ? raw : JSON.toJSONString(arguments);
                return new ResolvedAction(direct, remoteToolName, mcpId, remoteToolName, input);
            }
            throw new IllegalStateException(
                    "DIRECT_NODE_MCP_TOOL_UNAVAILABLE:" + mcpId + ":" + remoteToolName);
        }

        String toolName = text(action.get("toolName"));
        if (!StringUtils.hasText(toolName)) {
            throw new IllegalArgumentException("DIRECT_NODE_TOOL_NAME_REQUIRED:" + index);
        }
        ToolCallback tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalStateException("DIRECT_NODE_TOOL_UNAVAILABLE:" + toolName);
        }
        String input = arguments instanceof String raw ? raw : JSON.toJSONString(arguments);
        return new ResolvedAction(tool, toolName, "", toolName, input);
    }

    private List<Map<String, Object>> actions(OpsWorkflowNode node) {
        if (node == null || node.getConfig() == null) return List.of();
        Object raw = node.getConfig().get("actions");
        if (!(raw instanceof List<?> items)) return List.of();
        List<Map<String, Object>> actions = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> source)) {
                throw new IllegalArgumentException("DIRECT_NODE_ACTION_INVALID");
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            source.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            actions.add(normalized);
        }
        return List.copyOf(actions);
    }

    private Map<String, ToolCallback> toolsByName(List<ToolCallback> callbacks) {
        Map<String, ToolCallback> tools = new LinkedHashMap<>();
        if (callbacks == null) return tools;
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null) continue;
            String name = text(callback.getToolDefinition().name());
            if (StringUtils.hasText(name)) tools.putIfAbsent(name, callback);
        }
        return tools;
    }

    private record ResolvedAction(
            ToolCallback tool,
            String toolName,
            String mcpId,
            String callbackName,
            String input) {
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
