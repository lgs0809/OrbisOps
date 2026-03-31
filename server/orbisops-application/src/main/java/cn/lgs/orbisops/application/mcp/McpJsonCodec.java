package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.Map;

/** Pure application JSON helper for tolerant MCP catalog and policy payload handling. */
public final class McpJsonCodec {

    public Object decode(String json) {
        String normalized = json == null ? "" : json.trim();
        if (normalized.isBlank()) return Map.of();
        try {
            if (normalized.startsWith("[")) return CanonicalJson.parseArray(normalized);
            if (normalized.startsWith("{")) return CanonicalJson.parseObject(normalized);
            return Map.of();
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    public String encode(Object value) {
        return CanonicalJson.stringifyPreservingOrder(value == null ? Map.of() : value);
    }
}
