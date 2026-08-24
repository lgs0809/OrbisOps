package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.alibaba.fastjson.JSON;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Model-facing projection only; persisted MCP contracts and execution guards remain authoritative. */
final class OpsLandingMcpModelProjection {

    private final OpsLandingOperationBindingResolver bindings = new OpsLandingOperationBindingResolver();

    Map<String, Object> definition(OpsMcpServerConfig config, Map<String, Object> definition) {
        if (definition == null || !owned(config, String.valueOf(definition.get("name")))) return definition;
        Map<String, Object> projected = new LinkedHashMap<>(definition);
        // A change confined to an owned property must still invalidate a loaded
        // callback, even when its reduced model schema would otherwise look identical.
        projected.put("authoritativeDefinitionHash", CanonicalObjectHasher.sha256(definition));
        if (definition.get("inputSchema") instanceof Map<?, ?> schema) {
            projected.put("inputSchema", schema(schema));
        }
        projected.put("runtimeOwnedArguments", List.of("executionKey"));
        return Collections.unmodifiableMap(projected);
    }

    String schema(OpsMcpServerConfig config, String toolName, String schema) {
        if (!owned(config, toolName)) return schema;
        return JSON.toJSONString(schema(JSON.parseObject(schema)));
    }

    Map<String, Object> activation(OpsMcpServerConfig config, String toolName, Map<String, Object> result) {
        if (!owned(config, toolName) || result == null) return result;
        Map<String, Object> projected = new LinkedHashMap<>(result);
        for (String key : List.of("schema", "inputSchema")) {
            if (result.get(key) instanceof Map<?, ?> schema) projected.put(key, schema(schema));
        }
        projected.put("runtimeOwnedArguments", List.of("executionKey"));
        return Collections.unmodifiableMap(projected);
    }

    boolean owned(OpsMcpServerConfig config, String toolName) {
        return bindings.ownsExecutionKey(config, toolName);
    }

    private Map<String, Object> schema(Map<?, ?> source) {
        Map<String, Object> projected = new LinkedHashMap<>();
        source.forEach((key, value) -> projected.put(String.valueOf(key), value));
        if (source.get("properties") instanceof Map<?, ?> properties) {
            Map<String, Object> fields = new LinkedHashMap<>();
            properties.forEach((key, value) -> {
                if (!"executionKey".equals(key)) fields.put(String.valueOf(key), value);
            });
            projected.put("properties", fields);
        }
        if (source.get("required") instanceof List<?> required) {
            projected.put("required", required.stream().filter(key -> !"executionKey".equals(key)).toList());
        }
        // This describes the projected call contract, not a remote schema or policy mutation.
        projected.put("description", "平台执行器注入 executionKey；提交已批准的业务参数，不生成或复制执行身份。"
                + String.valueOf(source.containsKey("description") ? source.get("description") : ""));
        return projected;
    }
}
