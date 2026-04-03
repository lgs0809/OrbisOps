package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.LandingOperationExecutionBinding;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsLandingMcpModelProjectionTest {

    @Test
    void onlyServerBoundLandingSchemaProjectsExecutionIdentityAndPreservesBusinessFields() {
        var projection = new OpsLandingMcpModelProjection();
        var schema = schema();
        var definition = Map.<String, Object>of("name", "apply_configuration", "inputSchema", schema,
                "schemaHash", "authoritative-remote-schema-hash");
        var projected = projection.definition(config(), definition);
        var input = (Map<?, ?>) projected.get("inputSchema");
        assertFalse(((Map<?, ?>) input.get("properties")).containsKey("executionKey"));
        assertEquals(List.of("expectedVersion", "version", "configuration"), input.get("required"));
        assertEquals(fields().get("configuration"),
                ((Map<?, ?>) input.get("properties")).get("configuration"));
        assertEquals(definition.get("schemaHash"), projected.get("schemaHash"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("executionKey"));
        assertTrue(((List<?>) schema.get("required")).contains("executionKey"));

        var prepare = config();
        prepare.setToolCallStage("PREPARE");
        assertEquals(definition, projection.definition(prepare, definition));
        var foreign = config();
        foreign.setMcpId("foreign-mcp");
        foreign.setToolId("foreign-mcp");
        foreign.setName("foreign-mcp");
        assertEquals(definition, projection.definition(foreign, definition));
        var unbound = Map.<String, Object>of("name", "unapproved_write", "inputSchema", schema);
        assertEquals(unbound, projection.definition(config(), unbound));
    }

    @Test
    void ownedPropertySchemaChangeStillChangesLoadedDefinitionFingerprint() {
        var projection = new OpsLandingMcpModelProjection();
        var first = Map.<String, Object>of("name", "apply_configuration", "inputSchema", schema());
        var changedFields = new java.util.LinkedHashMap<>(fields());
        changedFields.put("executionKey", Map.of("type", "integer"));
        var changedSchema = new java.util.LinkedHashMap<>(schema());
        changedSchema.put("properties", changedFields);
        var second = Map.<String, Object>of("name", "apply_configuration", "inputSchema", changedSchema);
        var firstProjection = projection.definition(config(), first);
        var secondProjection = projection.definition(config(), second);
        assertEquals(firstProjection.get("inputSchema"), secondProjection.get("inputSchema"));
        assertFalse(firstProjection.get("authoritativeDefinitionHash")
                .equals(secondProjection.get("authoritativeDefinitionHash")));
    }

    @Test
    void modelOmittingOwnedKeyUsesJournalKeyWithoutChangingBusinessArguments() {
        var service = mock(OpsToolExecutionService.class);
        var config = config();
        var adapter = new OpsProgressiveMcpCallbackAdapter(() -> service);
        when(service.executeLandingMcp(eq(config), anyString(), eq("ops-agent"),
                eq("journal-key"), eq(false), eq("operation-1"))).thenReturn(Map.of("status", "SUCCEEDED"));
        var business = Map.of("expectedVersion", 7, "version", 8,
                "configuration", Map.of("executionKey", "business-data", "enabled", true));
        var discovered = discovered();
        var callback = adapter.directCallback(config, discovered, false);
        assertFalse(JSON.parseObject(callback.getToolDefinition().inputSchema())
                .getJSONObject("properties").containsKey("executionKey"));
        callback.call(JSON.toJSONString(business));

        var input = ArgumentCaptor.forClass(String.class);
        verify(service).executeLandingMcp(eq(config), input.capture(), eq("ops-agent"),
                eq("journal-key"), eq(false), eq("operation-1"));
        assertEquals(business, JSON.parseObject(input.getValue()).get("arguments"));
        verify(discovered, org.mockito.Mockito.never()).call(anyString());
    }

    @Test
    void explicitWrongKeyStillFailsBeforeUnifiedExecutionAndRemoteDispatch() {
        var service = mock(OpsToolExecutionService.class);
        var adapter = new OpsProgressiveMcpCallbackAdapter(() -> service);
        var callback = adapter.directCallback(config(), discovered(), false);
        var error = assertThrows(IllegalStateException.class,
                () -> callback.call("{\"expectedVersion\":7,\"version\":8,\"executionKey\":\"model-forged\"}"));
        assertTrue(error.getMessage().contains("LANDING_OPERATION_EXECUTION_BINDING_MISMATCH"));
        verifyNoInteractions(service);
    }

    @Test
    void ambiguousApprovedOperationCannotInventAnExecutionIdentity() {
        var service = mock(OpsToolExecutionService.class);
        var config = config();
        config.setLandingOperationBindings(List.of(binding(), new LandingOperationExecutionBinding(
                "operation-2", "journal-key-2", "mcp.ops-mcp", "apply_configuration", "service://second/prod")));
        var error = assertThrows(IllegalStateException.class,
                () -> new OpsProgressiveMcpCallbackAdapter(() -> service).directCallback(config, discovered(), false)
                        .call("{\"expectedVersion\":7,\"version\":8}"));
        assertTrue(error.getMessage().contains("LANDING_OPERATION_EXECUTION_BINDING_AMBIGUOUS"));
        verifyNoInteractions(service);
    }

    @Test
    void progressiveActivationAlsoProjectsOnlyModelViewOfRemoteSchema() {
        var service = mock(OpsToolExecutionService.class);
        var config = config();
        String input = "{\"toolName\":\"apply_configuration\"}";
        var original = Map.<String, Object>of("status", "ACTIVE", "toolName", "apply_configuration",
                "schema", schema(), "schemaHash", "original-hash");
        when(service.enableMcpTool(config, input, "ops-agent")).thenReturn(original);
        var result = JSON.parseObject(new OpsProgressiveMcpCallbackAdapter(() -> service)
                .enableCallback(config).call(input));
        assertFalse(result.getJSONObject("schema").getJSONObject("properties").containsKey("executionKey"));
        assertEquals("original-hash", result.getString("schemaHash"));
        assertTrue(((Map<?, ?>) ((Map<?, ?>) original.get("schema")).get("properties")).containsKey("executionKey"));
    }

    private static OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder().name("ops-mcp").mcpId("ops-mcp").toolId("ops-mcp")
                .toolCallStage("LANDING").landingOperationBindings(List.of(binding())).build();
    }

    private static LandingOperationExecutionBinding binding() {
        return new LandingOperationExecutionBinding("operation-1", "journal-key", "mcp.ops-mcp",
                "apply_configuration", "service://orders/prod");
    }

    private static ToolCallback discovered() {
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder().name("apply_configuration")
                .description("Apply approved configuration").inputSchema(JSON.toJSONString(schema())).build());
        return callback;
    }

    private static Map<String, Map<String, Object>> fields() {
        return Map.of("executionKey", Map.of("type", "string"), "expectedVersion", Map.of("type", "integer"),
                "version", Map.of("type", "integer"), "configuration", Map.of("type", "object",
                        "properties", Map.of("executionKey", Map.of("type", "string"))));
    }

    private static Map<String, Object> schema() {
        return Map.of("type", "object", "properties", fields(),
                "required", List.of("executionKey", "expectedVersion", "version", "configuration"),
                "additionalProperties", false);
    }
}
