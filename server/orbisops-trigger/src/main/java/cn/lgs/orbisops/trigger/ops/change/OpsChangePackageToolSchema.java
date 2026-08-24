package cn.lgs.orbisops.trigger.ops.change;

import com.alibaba.fastjson.JSON;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Model-facing structure; tool arguments and authorization remain owned by MCP policy. */
final class OpsChangePackageToolSchema {

    private static final String SCHEMA = create();

    private OpsChangePackageToolSchema() { }

    static String inputSchema() { return SCHEMA; }

    private static String create() {
        var schema = JSON.parseObject(JsonSchemaGenerator.generateForType(OpsChangePackageToolInput.class));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("operationId", text("本变更包内唯一的操作身份"));
        fields.put("mcpId", text("权威动作目录中的 MCP ID"));
        fields.put("toolName", text("权威动作目录中的工具名"));
        fields.put("purpose", text("仅验证操作可标记 PRE_APPROVAL_VALIDATION；权限由服务端核对"));
        fields.put("arguments", object("按所选 MCP 工具实际披露的输入 schema 填写，不填写 schema 本身"));
        fields.put("preconditions", object("生产写操作必填：执行前已观测资源身份、版本及可核对条件"));
        fields.put("postCheck", postCheck(true));
        fields.put("rollbackPlan", recovery("生产写操作必填：失败时的受控恢复方案；不代表已执行或已授权恢复"));
        fields.put("rollbackPrecondition", recovery("生产写操作必填：允许恢复的资源状态、版本和需要重新审批的条件"));
        fields.put("manualFallback", recovery("生产写操作必填：无法安全自动恢复时停止并交接的处置；必须是对象，不能直接填字符串"));
        Map<String, Object> action = object("可执行 MCP 操作。生产写必须提供所有安全结构；验证操作不要求生产恢复方案");
        action.put("properties", fields);
        action.put("required", List.of("operationId", "mcpId", "toolName", "arguments"));
        schema.getJSONObject("properties").put("actions", Map.of("type", "array", "items", action));
        return JSON.toJSONString(schema);
    }

    private static Map<String, Object> postCheck(boolean additionalAllowed) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("toolsetId", text("mcp. 加真实 MCP ID，工具必须只读"));
        fields.put("toolName", text("实际披露的只读工具名"));
        fields.put("arguments", object("按所选只读 MCP 工具输入 schema 填写的业务参数"));
        fields.put("resourceIdentityField", text("结果中资源身份字段名，默认 resourceKey"));
        var expected = object("必须包含与写操作相同的资源身份及真实目标值，支持点号路径，保留数字/布尔等原始类型");
        expected.put("minProperties", 1);
        fields.put("expectedValues", expected);
        if (additionalAllowed) fields.put("additionalChecks", Map.of(
                "type", "array", "maxItems", 15, "items", postCheck(false),
                "description", "其他必需只读后验，仅放在 postCheck 内，全部通过才成功"));
        Map<String, Object> result = object("生产写操作必填：审批后实际执行的只读后验");
        result.put("properties", fields);
        result.put("required", List.of("toolsetId", "toolName", "arguments", "expectedValues"));
        result.put("additionalProperties", false);
        return result;
    }

    private static Map<String, Object> recovery(String description) {
        var result = object(description);
        result.put("minProperties", 1);
        result.put("properties", Map.of("summary", text("具体处置或条件的自然语言说明")));
        return result;
    }

    private static Map<String, Object> object(String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("description", description);
        result.put("additionalProperties", true);
        return result;
    }

    private static Map<String, Object> text(String description) {
        return Map.of("type", "string", "description", description);
    }
}
