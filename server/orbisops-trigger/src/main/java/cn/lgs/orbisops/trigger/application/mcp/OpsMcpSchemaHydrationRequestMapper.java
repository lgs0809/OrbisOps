package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpAuthoritativeToolDefinition;
import cn.lgs.orbisops.application.mcp.McpRuntimeActivationRequest;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class OpsMcpSchemaHydrationRequestMapper {

    public McpSchemaHydrationRequest hydration(String projectId,
                                               Map<String, Object> request) {
        return hydration(projectId, request, Map.of());
    }

    public McpSchemaHydrationRequest hydration(String projectId,
                                               Map<String, Object> request,
                                               Map<String, Object> authoritativeDefinition) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new McpSchemaHydrationRequest(
                projectId,
                firstText(safe, "toolId", "mcpId"),
                firstText(safe, "remoteToolName", "toolName"),
                text(safe.get("agentId")),
                text(safe.get("userId")),
                authoritative(authoritativeDefinition));
    }

    public McpRuntimeActivationRequest activation(String projectId,
                                                  String runId,
                                                  String actor,
                                                  Map<String, Object> request,
                                                  Map<String, Object> authoritativeDefinition) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new McpRuntimeActivationRequest(
                projectId,
                runId,
                actor,
                firstText(safe, "mcpId", "toolId"),
                firstText(safe, "toolName", "remoteToolName"),
                text(safe.get("sessionId")),
                text(safe.get("agentId")),
                text(safe.get("userId")),
                text(safe.get("reason")),
                text(safe.getOrDefault("stage", "PREPARE")),
                false,
                authoritative(authoritativeDefinition));
    }

    public McpAuthoritativeToolDefinition authoritative(Map<String, Object> source) {
        Map<String, Object> safe = source == null ? Map.of() : source;
        return new McpAuthoritativeToolDefinition(
                text(safe.get("description")),
                first(safe, "inputSchema", "input_schema", "schema", "parameters"),
                first(safe, "outputSchema", "output_schema"),
                text(safe.get("schemaSource")));
    }

    private Object first(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) return source.get(key);
        }
        return null;
    }

    private String firstText(Map<String, Object> source, String... keys) {
        Object value = first(source, keys);
        return text(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
