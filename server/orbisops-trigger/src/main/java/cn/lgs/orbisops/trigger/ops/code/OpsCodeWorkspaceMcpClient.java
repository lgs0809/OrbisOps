package cn.lgs.orbisops.trigger.ops.code;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpRuntimeInvoker;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/** Thin anti-corruption client over the platform's existing MCP runtime. */
@Component
public final class OpsCodeWorkspaceMcpClient {

    private final OpsProjectMcpRuntimeConfigService configs;
    private final OpsMcpRuntimeInvoker invoker;

    public OpsCodeWorkspaceMcpClient(
            OpsProjectMcpRuntimeConfigService configs,
            OpsMcpRuntimeInvoker invoker) {
        if (configs == null) throw new IllegalArgumentException("CODE_MCP_RUNTIME_CONFIG_REQUIRED");
        if (invoker == null) throw new IllegalArgumentException("CODE_MCP_RUNTIME_INVOKER_REQUIRED");
        this.configs = configs;
        this.invoker = invoker;
    }

    public JSONObject invoke(
            String projectId,
            String mcpId,
            String toolName,
            Map<String, Object> arguments) {
        OpsMcpServerConfig config = configs.resolve(value(projectId), value(mcpId))
                .orElseThrow(() -> new IllegalStateException("CODE_MCP_NOT_CONFIGURED:" + value(mcpId)));
        String raw = invoker.invoke(config, required(toolName, "CODE_MCP_TOOL_REQUIRED"),
                JSON.toJSONString(arguments == null ? Map.of() : arguments));
        JSONObject result = payload(raw);
        if (result == null) throw new IllegalStateException("CODE_MCP_EMPTY_RESULT:" + toolName);
        return result;
    }

    JSONObject payload(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        Object parsed = JSON.parse(raw);
        if (parsed instanceof JSONArray array) {
            return payloadFromContent(array);
        }
        JSONObject object = parsed instanceof JSONObject json
                ? json
                : JSON.parseObject(JSON.toJSONString(parsed));
        if (object == null) return null;
        if (object.get("content") instanceof JSONArray content) {
            JSONObject payload = payloadFromContent(content);
            if (payload != null) return payload;
        }
        return object;
    }

    private JSONObject payloadFromContent(JSONArray content) {
        if (content == null || content.isEmpty()) return null;
        Object first = content.get(0);
        JSONObject item = first instanceof JSONObject json ? json : JSON.parseObject(JSON.toJSONString(first));
        String text = item == null ? "" : item.getString("text");
        if (!StringUtils.hasText(text)) return item;
        return JSON.parseObject(text);
    }

    private String required(String value, String code) {
        String result = value(value);
        if (result.isBlank()) throw new IllegalArgumentException(code);
        return result;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
