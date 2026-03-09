package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

/** Normalizes model-visible tool schemas without changing tool semantics. */
public final class OpsToolSchemaNormalizer {

    private OpsToolSchemaNormalizer() {
    }

    public static ToolDefinition normalize(ToolDefinition definition) {
        if (definition == null) {
            return null;
        }
        String schema = definition.inputSchema();
        if (!StringUtils.hasText(schema)) {
            throw new IllegalArgumentException("TOOL_SCHEMA_MISSING：工具 " + definition.name() + " 缺少输入 Schema");
        }
        try {
            JSONObject parsed = JSON.parseObject(schema);
            if (parsed == null) {
                throw new IllegalArgumentException("schema is null");
            }
            if ("object".equalsIgnoreCase(parsed.getString("type")) && !parsed.containsKey("properties")) {
                parsed.put("properties", new JSONObject(true));
            }
            return ToolDefinition.builder()
                    .name(definition.name())
                    .description(definition.description())
                    .inputSchema(parsed.toJSONString())
                    .build();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("TOOL_SCHEMA_INVALID：工具 " + definition.name() + " 的输入 Schema 无法解析", error);
        }
    }
}
