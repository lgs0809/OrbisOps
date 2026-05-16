package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds proposal-only observations for governed PROD mutations without invoking remote tools. */
final class OpsMcpProposedActionFactory {

    boolean applies(OpsMcpServerConfig config) {
        return config != null
                && !landing(config)
                && "PREPARE_CHANGE".equalsIgnoreCase(value(config.getRuntimeAuthority()))
                && productionResource(config);
    }

    Map<String, Object> create(
            OpsMcpServerConfig config,
            String toolName,
            Map<String, Object> arguments) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("toolName", value(toolName));
        action.put("arguments", arguments == null ? Map.of() : arguments);
        action.put("mcpId", config == null ? "" : firstText(config.getMcpId(), config.getName()));
        action.put("projectId", config == null ? "" : value(config.getProjectId()));
        action.put("runId", config == null ? "" : value(config.getRunId()));
        action.put("nodeId", config == null ? "" : value(config.getNodeId()));
        action.put("effect", "TARGET_RESOURCE_WRITE");
        action.put("exposure", "PROPOSABLE_ONLY");
        action.put("remoteCallExecuted", false);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("status", "REQUIRES_CHANGE_PACKAGE");
        envelope.put("allowed", false);
        envelope.put("remoteCallExecuted", false);
        envelope.put("proposedAction", Map.copyOf(action));
        envelope.put("agentObservation",
                "已记录受治理生产变更意图；普通 Runtime 未调用真实 PROD MCP，请基于该 ProposedAction 形成 ChangePackage。");
        return Map.copyOf(envelope);
    }

    private boolean productionResource(OpsMcpServerConfig config) {
        if (config == null || config.getToolCapabilities() == null) return false;
        String environment = value(config.getToolCapabilities().get("resourceEnvironment")).toLowerCase();
        return "prod".equals(environment) || "production".equals(environment);
    }

    private boolean landing(OpsMcpServerConfig config) {
        return config != null && "LANDING".equalsIgnoreCase(value(config.getToolCallStage()));
    }

    private String firstText(Object... values) {
        if (values == null) return "";
        for (Object candidate : values) {
            String text = value(candidate);
            if (!text.isBlank()) return text;
        }
        return "";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
