package cn.lgs.orbisops.trigger.ops.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/** SDK boundary: never discard structuredContent, multi-content, isError or _meta. */
final class OpsMcpFullResultToolCallback implements ToolCallback {
    private final OpsMcpClientRegistry.ClientHandle handle;
    private final McpSchema.Tool tool;
    private final Runnable assertCurrent;
    private final ToolDefinition definition;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final OpsMcpCallResultNormalizer results = new OpsMcpCallResultNormalizer();
    private final io.modelcontextprotocol.json.schema.JsonSchemaValidator schemas =
            new io.modelcontextprotocol.json.schema.jackson.JacksonJsonSchemaValidatorSupplier().get();

    OpsMcpFullResultToolCallback(OpsMcpClientRegistry.ClientHandle handle, McpSchema.Tool tool) {
        this(handle,tool,()->{});
    }

    OpsMcpFullResultToolCallback(OpsMcpClientRegistry.ClientHandle handle, McpSchema.Tool tool, Runnable assertCurrent) {
        this.assertCurrent=assertCurrent;
        this.handle = handle;
        this.tool = tool;
        try {
            definition = ToolDefinition.builder().name(tool.name())
                    .description(tool.description() == null ? tool.name() : tool.description())
                    .inputSchema(json.writeValueAsString(tool.inputSchema())).build();
        } catch (JsonProcessingException error) { throw new IllegalArgumentException("MCP_INPUT_SCHEMA_INVALID", error); }
    }

    @Override public ToolDefinition getToolDefinition() { return definition; }
    Map<String,Object> definitionSnapshot() {
        return json.convertValue(tool,new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }
    Map<String, Object> outputSchema() { return tool.outputSchema() == null ? Map.of() : tool.outputSchema(); }

    @Override
    public String call(String input) {
        handle.assertValid();
        assertCurrent.run();
        Map<String, Object> arguments;
        try {
            arguments = json.readValue(input, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            if (arguments == null) throw new OpsMcpCallFailure(OpsMcpCallFailure.Kind.CONTRACT_INVALID, "ARGUMENTS_OBJECT_REQUIRED", false);
        } catch (JsonProcessingException invalid) {
            throw new OpsMcpCallFailure(OpsMcpCallFailure.Kind.CONTRACT_INVALID, "ARGUMENTS_JSON_INVALID", false, invalid);
        }
        Map<String, Object> inputSchema = json.convertValue(tool.inputSchema(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        OpsMcpCallResultNormalizer.rejectRemoteReferences(inputSchema);
        OpsMcpRequestScope.assertContract(inputSchema, outputSchema());
        arguments = OpsMcpRequestScope.runtimeOwnedArguments(inputSchema, arguments);
        try {
            if (!schemas.validate(inputSchema, arguments).valid()) {
                throw new OpsMcpCallFailure(OpsMcpCallFailure.Kind.CONTRACT_INVALID, "ARGUMENTS_SCHEMA_MISMATCH", false);
            }
        } catch (OpsMcpCallFailure invalid) { throw invalid; }
        catch (RuntimeException invalid) {
            throw new OpsMcpCallFailure(OpsMcpCallFailure.Kind.CONTRACT_INVALID, "INPUT_SCHEMA_INVALID", false, invalid);
        }
        McpSchema.CallToolResult result;
        try { result = handle.client().callTool(new McpSchema.CallToolRequest(tool.name(), arguments)); }
        catch (RuntimeException error) { throw OpsMcpFailureClassifier.classify(error, OpsMcpRequestScope.currentDispatched()); }
        return results.normalize(result, tool.outputSchema());
    }
}
