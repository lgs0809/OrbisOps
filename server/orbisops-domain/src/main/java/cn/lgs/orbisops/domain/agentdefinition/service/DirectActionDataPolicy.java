package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleField;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit data movement between governed tools, without expressions or access to runtime authority. */
public final class DirectActionDataPolicy {
    private static final int MAX_BYTES = 1_048_576;

    public void validate(Map<?, ?> action) {
        if (action.containsKey("structuredOutputKey")) key(action.get("structuredOutputKey"));
        if (action.containsKey("outputMode")) {
            if (!java.util.Set.of("FULL", "MCP_EVIDENCE_REFERENCE").contains(String.valueOf(action.get("outputMode")))) {
                throw new IllegalArgumentException("DIRECT_OUTPUT_MODE_INVALID");
            }
            if ("MCP_EVIDENCE_REFERENCE".equals(action.get("outputMode"))
                    && (!(action.get("mcpId") instanceof String mcp) || mcp.isBlank()
                    || !(action.get("remoteToolName") instanceof String tool) || tool.isBlank()
                    || !action.containsKey("structuredOutputKey"))) {
                throw new IllegalArgumentException("DIRECT_MCP_EVIDENCE_BINDING_REQUIRED");
            }
        }
        if (!action.containsKey("argumentBindings")) return;
        if (!(action.get("argumentBindings") instanceof Map<?, ?> bindings) || bindings.size() > 64) {
            throw new IllegalArgumentException("DIRECT_ARGUMENT_BINDINGS_INVALID");
        }
        if (action.containsKey("arguments") && !(action.get("arguments") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("DIRECT_BOUND_ARGUMENTS_REQUIRE_OBJECT");
        }
        var literals = action.get("arguments") instanceof Map<?, ?> map ? map : Map.of();
        bindings.forEach((name, field) -> {
            key(name);
            path(field);
            if (literals.containsKey(name)) throw new IllegalArgumentException("DIRECT_BINDING_LITERAL_CONFLICT:" + name);
        });
    }

    public boolean needsInput(Map<?, ?> action) {
        return action.get("argumentBindings") instanceof Map<?, ?> bindings
                && bindings.values().stream().anyMatch(field -> path(field).root().equals("input"));
    }

    public Object arguments(Map<?, ?> action, Map<String, Object> input, Map<String, Object> workflowData) {
        validate(action);
        Object raw = action.containsKey("arguments") ? action.get("arguments") : Map.of();
        if (!(action.get("argumentBindings") instanceof Map<?, ?> bindings) || bindings.isEmpty()) return raw;
        var result = new LinkedHashMap<String, Object>();
        if (raw instanceof Map<?, ?> literals) literals.forEach((k, v) -> result.put(String.valueOf(k), v));
        bindings.forEach((name, source) -> {
            var field = path(source);
            Object value = field.root().equals("input") ? input : workflowData;
            for (String segment : field.nestedSegments()) {
                value = value instanceof Map<?, ?> map ? map.get(segment) : null;
            }
            if (value == null) throw new IllegalArgumentException("DIRECT_BINDING_VALUE_MISSING:" + field.path());
            result.put(String.valueOf(name), value);
        });
        // Detach mutable nested objects before callbacks receive them.
        return parseObject(CanonicalJson.stringifyPreservingOrder(result));
    }

    public Map<String, Object> parseObject(String value) {
        String json = value == null ? "" : value;
        if (json.isBlank() || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("DIRECT_STRUCTURED_DATA_SIZE_INVALID");
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
            } else if (ch == '"') quoted = true;
            else if (ch == '{' || ch == '[') {
                if (++depth > 32) throw new IllegalArgumentException("DIRECT_STRUCTURED_DATA_DEPTH_EXCEEDED");
            } else if (ch == '}' || ch == ']') depth--;
        }
        return CanonicalJson.parseObject(json);
    }

    private WorkflowRuleField path(Object value) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("DIRECT_BINDING_PATH_REQUIRED");
        var field = new WorkflowRuleField(text);
        if (!field.root().equals("input") && !field.path().startsWith("nodeOutput.workflowData_")) {
            throw new IllegalArgumentException("DIRECT_BINDING_AUTHORITY_ACCESS_FORBIDDEN:" + text);
        }
        return field;
    }

    private String key(Object value) {
        if (!(value instanceof String key) || !key.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("DIRECT_DATA_KEY_INVALID");
        }
        return key;
    }
}
