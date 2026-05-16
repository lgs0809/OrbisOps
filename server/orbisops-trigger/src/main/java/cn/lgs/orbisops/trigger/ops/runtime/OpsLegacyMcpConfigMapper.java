package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.McpClientDefinition;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps the legacy MCP persistence record into the runtime transport contract. */
@Slf4j
public final class OpsLegacyMcpConfigMapper {

    public OpsMcpServerConfig map(McpClientDefinition mcp) {
        if (mcp == null) {
            throw new IllegalArgumentException("LEGACY_MCP_CONFIG_REQUIRED");
        }
        String transport = firstText(mcp.transportType(), "stdio").trim().toLowerCase();
        OpsMcpServerConfig.OpsMcpServerConfigBuilder builder = OpsMcpServerConfig.builder()
                .name(mcp.mcpName())
                .description("MCP工具库：" + mcp.mcpId())
                .transport(transport)
                .timeoutSeconds(mcp.requestTimeout());
        Map<String, Object> root = parseJsonMap(mcp.transportConfig());
        builder.toolCapabilities(stringMap(root.get("toolCapabilities")))
                .allowedTools(stringList(root.get("allowedTools")))
                .notificationTools(stringList(root.get("notificationTools")))
                .blockedTools(stringList(root.get("blockedTools")))
                .headers(stringMap(root.get("headers")));
        if ("sse".equals(transport) || "streamable-http".equals(transport)) {
            String baseUri = stringValue(root.get("baseUri"));
            String endpoint = firstText(
                    stringValue(root.get("sseEndpoint")),
                    stringValue(root.get("endpoint")),
                    "sse".equals(transport) ? "/sse" : "/mcp");
            builder.url(joinUrl(baseUri, endpoint));
            return builder.build();
        }
        Object selected = root.get(mcp.mcpName());
        Map<String, Object> stdio = selected instanceof Map<?, ?> map
                ? toStringObjectMap(map)
                : root;
        builder.command(stringValue(stdio.get("command")))
                .args(stringList(stdio.get("args")))
                .env(stringMap(stdio.get("env")));
        return builder.build();
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.parseObject(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析 MCP 配置 JSON 失败：{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of();
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), String.valueOf(item)));
        }
        return result;
    }

    private String joinUrl(String baseUri, String endpoint) {
        String base = value(baseUri);
        String path = value(endpoint);
        if (!StringUtils.hasText(path)) {
            return base;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (base.endsWith("/") && base.length() > 1) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
