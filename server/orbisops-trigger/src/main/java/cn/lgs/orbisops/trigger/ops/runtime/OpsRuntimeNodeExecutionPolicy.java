package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Interprets workflow node type, mode and agent role into the canonical execution node type. */
final class OpsRuntimeNodeExecutionPolicy {

    private final OpsAnalysisRoutingPolicy analysisRoutingPolicy;

    OpsRuntimeNodeExecutionPolicy(OpsAnalysisRoutingPolicy analysisRoutingPolicy) {
        this.analysisRoutingPolicy = analysisRoutingPolicy;
    }

    String executionNodeType(OpsWorkflowNode node) {
        if (node == null) {
            return "CHAT";
        }
        String type = normalizeType(node.getType());
        if (!"AGENT".equals(type)) {
            return type;
        }
        String mode = normalizeAgentMode(node);
        if ("DIRECT".equals(mode)) {
            return "DIRECT";
        }
        if ("LLM".equals(mode)) {
            return "AGENT";
        }
        if ("PLAN".equals(mode)) {
            return "PLAN";
        }
        if ("REACT".equals(mode)) {
            return "AGENTSCOPE";
        }
        if ("REVIEW".equals(mode)) {
            return "REVIEW";
        }

        String role = analysisRoutingPolicy.agentRole(node);
        String rawAgent = value(node.getAgent())
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_");
        if ("reviewer".equals(role)) {
            return "REVIEW";
        }
        if ("reporter".equals(role) || rawAgent.contains("report")) {
            return "REPORT";
        }
        if ("notifier".equals(role)
                || rawAgent.contains("channel")
                || rawAgent.contains("notify")) {
            return "NOTIFY";
        }
        if ("main_planner".equals(role)) {
            return "PLAN";
        }
        if ("data_agent".equals(role)) {
            return "AGENTSCOPE";
        }
        return "AGENT";
    }

    String normalizeAgentMode(OpsWorkflowNode node) {
        String configured = firstText(
                node == null ? null : node.getMode(),
                configText(node, "mode"),
                configText(node, "agentMode"));
        String mode = StringUtils.hasText(configured)
                ? configured.trim().toUpperCase(Locale.ROOT).replace('-', '_')
                : "";
        if ("REVIEW".equals(mode)) {
            return "LLM";
        }
        if (Set.of("DIRECT", "LLM", "REACT", "AUTO", "PLAN").contains(mode)) {
            return mode;
        }
        if (node != null && "AGENTSCOPE".equals(normalizeType(node.getSubEngine()))) {
            return "REACT";
        }
        String type = normalizeType(node == null ? null : node.getType());
        if ("PLAN".equals(type) || "ROUTER".equals(type)) {
            return "PLAN";
        }
        if ("REVIEW".equals(type) || "REFLECT".equals(type)) {
            return "REVIEW";
        }
        if (Set.of(
                "SUB_AGENT",
                "EXECUTE",
                "AGENTSCOPE",
                "RAG",
                "MCP",
                "TOOL_CALL",
                "KNOWLEDGE_RETRIEVAL").contains(type)) {
            return "REACT";
        }
        return "AUTO";
    }

    String normalizeType(String type) {
        return StringUtils.hasText(type)
                ? type.trim().toUpperCase(Locale.ROOT).replace('-', '_')
                : "CHAT";
    }

    String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String candidate : values) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private String configText(OpsWorkflowNode node, String key) {
        if (node == null || node.getConfig() == null || !StringUtils.hasText(key)) {
            return "";
        }
        Map<String, Object> config = node.getConfig();
        Object value = config.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
