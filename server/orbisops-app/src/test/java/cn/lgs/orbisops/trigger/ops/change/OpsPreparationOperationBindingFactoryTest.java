package cn.lgs.orbisops.trigger.ops.change;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsPreparationOperationBindingFactoryTest {

    private final OpsPreparationOperationBindingFactory factory =
            new OpsPreparationOperationBindingFactory();

    @Test
    void legacyAliasesAndPackageEnvironmentAreNormalizedOnce() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "legacy-op");
        raw.put("remoteToolName", "validate_patch");
        raw.put("mcpId", "mcp-1");
        raw.put("adapterType", "MCP");
        raw.put("effectType", "mutate_temp_resource");
        raw.put("effectScope", "sandbox");
        raw.put("mutability", "test_mutating");
        raw.put("riskLevel", "medium");
        raw.put("readOnly", false);
        raw.put("writesTargetResource", false);
        raw.put("requiresChangePackage", false);
        raw.put("requiresApproval", false);
        raw.put("policyBound", true);
        raw.put("schemaHash", "sha256:test");
        raw.put("targetResourceScope", "sandbox:workspace-1");
        raw.put("args", Map.of("path", "src/App.java"));
        OpsPreparationOperationBindingFactory.OperationBundle bundle = factory.create(
                List.of(raw),
                Map.of("target_environment", "validation"));
        raw.put("remoteToolName", "mutated-after-create");

        Map<String, Object> operation = bundle.operations().get(0);
        Map<String, Object> binding = bundle.toolBindings().get(0);
        assertEquals("legacy-op", operation.get("operationId"));
        assertEquals("validate_patch", operation.get("toolName"));
        assertEquals("MUTATE_EPHEMERAL", operation.get("effectType"));
        assertEquals("validation", operation.get("targetEnvironment"));
        assertEquals(Map.of("path", "src/App.java"), operation.get("arguments"));
        assertEquals("MCP", binding.get("adapterType"));
        assertEquals("validation", binding.get("targetEnvironment"));
        assertEquals("MUTATE_EPHEMERAL", binding.get("effectType"));
        assertEquals(false, binding.get("readOnly"));
        assertEquals(false, binding.get("writesTargetResource"));
        assertEquals(false, binding.get("requiresChangePackage"));
        assertEquals(false, binding.get("requiresApproval"));
        assertEquals(true, binding.get("schemaBound"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> operation.put("toolName", "forbidden-mutation"));
    }

    @Test
    void missingIdentityGetsDeterministicIdAndFailClosedBinding() {
        OpsPreparationOperationBindingFactory.OperationBundle bundle = factory.create(
                List.of(Map.of("name", "query_health")),
                Map.of());

        Map<String, Object> operation = bundle.operations().get(0);
        Map<String, Object> binding = bundle.toolBindings().get(0);
        assertEquals("op-1", operation.get("operationId"));
        assertEquals("query_health", operation.get("toolName"));
        assertEquals("", operation.get("mcpId"));
        assertEquals("UNKNOWN", operation.get("effectType"));
        assertEquals("UNKNOWN", operation.get("mutability"));
        assertEquals(false, binding.get("schemaBound"));
        @SuppressWarnings("unchecked")
        Map<String, Object> preconditions =
                (Map<String, Object>) operation.get("preconditions");
        assertEquals(false, preconditions.get("schemaValid"));
        assertEquals(false, preconditions.get("permissionGranted"));
        assertEquals("UNKNOWN", preconditions.get("targetExists"));
    }

    @Test
    void operationEnvironmentOverridesPackageEnvironmentAndProvidedPreconditions() {
        Map<String, Object> raw = Map.ofEntries(
                Map.entry("operationId", "op-1"),
                Map.entry("toolName", "query_health"),
                Map.entry("mcpId", "mcp-1"),
                Map.entry("effectType", "READ_EXTERNAL_STATE"),
                Map.entry("effectScope", "PLATFORM_INTERNAL"),
                Map.entry("mutability", "READ_ONLY"),
                Map.entry("policyBound", true),
                Map.entry("schemaHash", "sha256:test"),
                Map.entry("environment", "staging"),
                Map.entry("preconditions", Map.of("schemaValid", true, "permissionGranted", true)));

        OpsPreparationOperationBindingFactory.OperationBundle bundle = factory.create(
                List.of(raw),
                Map.of("targetEnvironment", "production"));

        Map<String, Object> operation = bundle.operations().get(0);
        assertEquals("staging", operation.get("targetEnvironment"));
        assertEquals(
                Map.of("schemaValid", true, "permissionGranted", true),
                operation.get("preconditions"));
        assertEquals(true, bundle.toolBindings().get(0).get("schemaBound"));
    }

    @Test
    void explicitRequestToolBindingsCannotAssertSchemaAuthority() {
        Map<String, Object> provided = new LinkedHashMap<>();
        provided.put("operationId", "op-1");
        provided.put("schemaBound", true);
        provided.put("schemaHash", "forged");

        OpsPreparationOperationBindingFactory.OperationBundle bundle = factory.create(
                List.of(),
                Map.of("toolBindings", List.of(provided)));

        assertTrue(bundle.operations().isEmpty());
        assertTrue(bundle.toolBindings().isEmpty());
    }
}
