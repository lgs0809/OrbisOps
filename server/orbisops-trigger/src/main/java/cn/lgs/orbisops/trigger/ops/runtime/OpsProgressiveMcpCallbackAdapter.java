package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Builds progressive-disclosure callbacks that can only execute through the unified Tool Execution facade. */
public final class OpsProgressiveMcpCallbackAdapter {

    private final Supplier<OpsToolExecutionService> toolExecutionServiceSupplier;
    private final OpsMcpUnifiedToolExecutor unifiedExecutor;

    public OpsProgressiveMcpCallbackAdapter(
            Supplier<OpsToolExecutionService> toolExecutionServiceSupplier) {
        if (toolExecutionServiceSupplier == null) {
            throw new IllegalArgumentException("OPS_TOOL_EXECUTION_SERVICE_SUPPLIER_REQUIRED");
        }
        this.toolExecutionServiceSupplier = toolExecutionServiceSupplier;
        this.unifiedExecutor = new OpsMcpUnifiedToolExecutor(toolExecutionServiceSupplier);
    }

    public ToolCallback dispatcherCallback(OpsMcpServerConfig config,
                                           List<Map<String, Object>> runtimeTools) {
        String toolId = value(config.getToolId(), value(config.getMcpId(), config.getName()));
        String callbackName = safeToolCallbackName("project_mcp_" + toolId);
        List<String> activeToolNames = runtimeTools == null ? List.of() : runtimeTools.stream()
                .filter(item -> "CORE".equalsIgnoreCase(value(item.get("disclosureTier"), "EXTENSION")))
                .map(item -> value(item.get("toolName")))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        String allowedTools = activeToolNames.isEmpty()
                ? "扩展工具需要先查询 tool_catalog 并用 enable_tool 激活。"
                : "可直接请求的核心工具：" + String.join(", ", activeToolNames)
                + "；扩展工具先用 enable_tool 激活。";
        String toolPolicySummary = runtimeTools == null || runtimeTools.isEmpty()
                ? ""
                : "\n这些工具均已由平台 Tool Policy 审核为审核前可执行；未审核、过期、禁用或需要 ChangePackage 的工具不会暴露给模型。";
        String description = value(config.getDescription());
        if (!StringUtils.hasText(description)) {
            description = "当前项目授权的 MCP 工具入口。";
        }
        String finalDescription = description + "\n"
                + "这是渐进式 MCP 调度入口，只暴露项目工具摘要，不预加载完整远端 schema。"
                + "调用时必须提供 toolName 和 arguments，运行时会校验项目授权、按需 hydrate schema、再执行远端 MCP 工具。"
                + allowedTools
                + toolPolicySummary;
        return callback(
                callbackName,
                finalDescription,
                """
                        {
                          "type": "object",
                          "properties": {
                            "toolName": {
                              "type": "string",
                              "description": "要调用的远端 MCP 工具名；如果项目工具仅授权一个远端工具，可省略。"
                            },
                            "arguments": {
                              "type": "object",
                              "description": "传给远端 MCP 工具的参数。敏感字段会在审计中脱敏。"
                            }
                          },
                          "required": ["arguments"]
                        }
                        """,
                (input, toolContext) -> unifiedExecutor.executeDispatcher(
                        config, input, toolContext, runtimeTools, callbackName));
    }

    public ToolCallback catalogCallback(OpsMcpServerConfig config) {
        return callback(
                safeToolCallbackName("mcp_tool_catalog_"
                        + value(config.getToolId(), value(config.getMcpId(), config.getName()))),
                "列出当前项目已授权、已审核且可在审核前使用的 MCP 工具压缩目录。只返回名称、能力、风险和一行说明，不加载完整 schema。",
                """
                        {"type":"object","properties":{}}
                        """,
                (input, toolContext) -> service().discoverMcpTools(config, "ops-agent"));
    }

    public ToolCallback enableCallback(OpsMcpServerConfig config) {
        return callback(
                safeToolCallbackName("enable_mcp_tool_"
                        + value(config.getToolId(), value(config.getMcpId(), config.getName()))),
                "按需激活当前项目工具目录中的一个扩展 MCP 工具。激活后返回审核策略和完整 schema，下一次推理才可调用。",
                """
                        {"type":"object","properties":{"toolName":{"type":"string"},"reason":{"type":"string"}},"required":["toolName"]}
                        """,
                (input, toolContext) -> service().enableMcpTool(config, input, "ops-agent"));
    }

    /**
     * Rebinds an eagerly discovered MCP callback to the unified Tool Execution path.
     * The discovered callback supplies only schema/metadata; invocation never bypasses
     * ToolExecutionApplicationService.
     */
    public ToolCallback directCallback(
            OpsMcpServerConfig config,
            ToolCallback discovered,
            boolean readOnly) {
        if (discovered == null || discovered.getToolDefinition() == null) {
            throw new IllegalArgumentException("MCP_DISCOVERED_TOOL_CALLBACK_REQUIRED");
        }
        String callbackName = discovered.getToolDefinition().name();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return discovered.getToolDefinition();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return discovered.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return JSON.toJSONString(unifiedExecutor.executeDirect(
                        config, callbackName, toolInput, null, readOnly));
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return JSON.toJSONString(unifiedExecutor.executeDirect(
                        config, callbackName, toolInput, toolContext, readOnly));
            }
        };
    }

    private ToolCallback callback(String name,
                                  String description,
                                  String schema,
                                  ToolAction action) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(schema)
                        .build();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                return JSON.toJSONString(action.apply(toolInput, null));
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return JSON.toJSONString(action.apply(toolInput, toolContext));
            }
        };
    }

    private OpsToolExecutionService service() {
        OpsToolExecutionService service = toolExecutionServiceSupplier.get();
        if (service == null) {
            throw new SecurityException("OpsToolExecutionService 未初始化，MCP 渐进披露不能绕过统一入口");
        }
        return service;
    }

    @FunctionalInterface
    private interface ToolAction {
        Map<String, Object> apply(String input, ToolContext toolContext);
    }

    static String safeToolCallbackName(String rawValue) {
        String normalized = StringUtils.hasText(rawValue)
                ? rawValue.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_")
                : "project_mcp_tool";
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : "project_mcp_tool";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String value(Object value, String fallback) {
        String text = value(value);
        return StringUtils.hasText(text) ? text : value(fallback);
    }
}
