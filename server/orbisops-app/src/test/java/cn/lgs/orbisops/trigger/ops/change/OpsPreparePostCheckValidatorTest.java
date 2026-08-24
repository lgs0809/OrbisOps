package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpAuthoritativeSchemaHydrationService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpsPreparePostCheckValidatorTest {
    private final OpsMcpAuthoritativeSchemaHydrationService schemas = mock(OpsMcpAuthoritativeSchemaHydrationService.class);
    private final McpToolPolicy policy = mock(McpToolPolicy.class);
    private final McpHydratedToolSchema schema = mock(McpHydratedToolSchema.class);

    private OpsPreparePostCheckValidator validator() {
        when(policy.activeAndHumanReviewed()).thenReturn(true);
        when(policy.readOnly()).thenReturn(true);
        when(policy.landAllowed()).thenReturn(true);
        when(policy.mcpId()).thenReturn("control");
        when(policy.schemaHash()).thenReturn("reviewed-schema");
        when(schema.schemaHydrated()).thenReturn(true);
        when(schema.schemaHash()).thenReturn("reviewed-schema");
        when(schema.inputSchema()).thenReturn(Map.of("type", "object", "required", List.of("projectId"),
                "properties", Map.of("projectId", Map.of("type", "string", "enum", List.of("project")))));
        when(schemas.hydrate(eq("project"), any())).thenReturn(schema);
        return new OpsPreparePostCheckValidator(schemas);
    }

    private Map<String, Object> check(String project) {
        return Map.of("toolsetId", "mcp.control", "toolName", "read", "arguments", Map.of("projectId", project),
                "expectedValues", Map.of("resourceKey", "resource", "version", "v2"));
    }

    @Test void incorrectFutureArgumentsFailBeforeAnyTargetMutation() {
        var validator = validator();
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validate("project", check("invented"), policy, "resource"))
                .getMessage().contains("ARGUMENTS_SCHEMA_MISMATCH"));
        assertDoesNotThrow(() -> validator.validate("project", check("project"), policy, "resource"));
        verify(schemas, times(2)).hydrate("project", Map.of("mcpId", "control", "remoteToolName", "read"));
    }

    @Test void unknownWritePolicyAndOtherResourceAreRejectedBeforeSchemaInspection() {
        var validator = validator();
        assertThrows(IllegalArgumentException.class, () -> validator.validate("project", check("project"), null, "resource"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("project", check("project"), policy, "other"));
        when(policy.readOnly()).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> validator.validate("project", check("project"), policy, "resource"));
        verifyNoInteractions(schemas);
    }

    @Test void schemaDriftAndRemoteReferencesCannotBeApprovedAsFutureChecks() {
        var validator = validator();
        when(schema.schemaHash()).thenReturn("changed");
        assertThrows(IllegalArgumentException.class, () -> validator.validate("project", check("project"), policy, "resource"));
        when(schema.schemaHash()).thenReturn("reviewed-schema");
        when(schema.inputSchema()).thenReturn(Map.of("$ref", "https://untrusted.invalid/schema"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("project", check("project"), policy, "resource"));
    }
}
