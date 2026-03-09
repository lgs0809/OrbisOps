package cn.lgs.orbisops.application.mcpexecution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed application result for the progressively disclosed MCP tool catalog. */
public record McpRuntimeCatalog(List<Map<String, Object>> tools) {

    public McpRuntimeCatalog {
        tools = tools == null
                ? List.of()
                : tools.stream().map(McpRuntimeCatalog::immutableCopy).toList();
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
