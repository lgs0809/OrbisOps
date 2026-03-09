package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Locale;
import java.util.Map;

/** Canonical node language published by the Agent Workflow bounded context. */
public enum AgentWorkflowNodeType {
    START,
    END,
    LLM,
    AGENT,
    TOOL,
    RAG,
    SKILL,
    CONDITION,
    HUMAN_APPROVAL,
    PARALLEL,
    JOIN,
    LOOP,
    SUB_WORKFLOW,
    WAIT,
    COMPENSATION;

    private static final Map<String, AgentWorkflowNodeType> PUBLISHED_NAMES = Map.ofEntries(
            Map.entry("START", START),
            Map.entry("END", END),
            Map.entry("CHAT", LLM),
            Map.entry("LLM", LLM),
            Map.entry("PLAN", LLM),
            Map.entry("REPORT", LLM),
            Map.entry("TEMPLATE", LLM),
            Map.entry("VARIABLE_MERGE", LLM),
            Map.entry("CUSTOM", LLM),
            Map.entry("AGENT", AGENT),
            Map.entry("SUB_AGENT", AGENT),
            Map.entry("AGENTSCOPE", AGENT),
            Map.entry("TOOL", TOOL),
            Map.entry("MCP", TOOL),
            Map.entry("TOOL_CALL", TOOL),
            Map.entry("HTTP", TOOL),
            Map.entry("CODE", TOOL),
            Map.entry("EXECUTE", TOOL),
            Map.entry("NOTIFY", TOOL),
            Map.entry("RAG", RAG),
            Map.entry("KNOWLEDGE_RETRIEVAL", RAG),
            Map.entry("SKILL", SKILL),
            Map.entry("CONDITION", CONDITION),
            Map.entry("ROUTER", CONDITION),
            Map.entry("REVIEW", CONDITION),
            Map.entry("REFLECT", CONDITION),
            Map.entry("HUMAN_APPROVAL", HUMAN_APPROVAL),
            Map.entry("PARALLEL", PARALLEL),
            Map.entry("JOIN", JOIN),
            Map.entry("LOOP", LOOP),
            Map.entry("SUB_WORKFLOW", SUB_WORKFLOW),
            Map.entry("WAIT", WAIT),
            Map.entry("COMPENSATION", COMPENSATION));

    public static AgentWorkflowNodeType fromPublishedName(String value) {
        String published = normalizePublishedName(value);
        AgentWorkflowNodeType type = PUBLISHED_NAMES.get(published);
        if (type == null) {
            throw new IllegalArgumentException("WORKFLOW_NODE_TYPE_UNKNOWN:" + published);
        }
        return type;
    }

    /**
     * Blank type is the documented legacy CHAT default. Unknown non-blank values never degrade.
     */
    public static String normalizePublishedName(String value) {
        if (value == null || value.trim().isBlank()) return "CHAT";
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
