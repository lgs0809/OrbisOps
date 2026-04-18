package cn.lgs.orbisops.trigger.ops.change;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsPreApprovalOperationMapperTest {

    @Test
    void mapperPreservesFieldPresenceAliasesAndExecutionValues() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("operationId", "op-1");
        raw.put("toolName", "local_name");
        raw.put("remoteToolName", "remote_name");
        raw.put("adapterType", "MCP");
        raw.put("mcpId", "mcp-primary");
        raw.put("toolsetId", "mcp-legacy");
        raw.put("arguments", Map.of("key", "value"));
        raw.put("resourceScope", "demo-project/config");
        raw.put("targetEnvironment", "test");
        raw.put("riskLevel", "HIGH");
        raw.put("effectType", "MUTATE_TEMP_RESOURCE");
        raw.put("effectScope", "TEST");
        raw.put("mutability", "TEMP_MUTATING");
        raw.put("readOnly", false);
        raw.put("writesTargetResource", false);
        raw.put("requiresChangePackage", true);
        raw.put("requiresApproval", true);

        OpsPreApprovalOperationMapper.MappedOperation mapped =
                new OpsPreApprovalOperationMapper().map(List.of(raw)).get(0);

        assertEquals("mcp-primary", mapped.facts().mcpId());
        assertEquals("MUTATE_EPHEMERAL", mapped.facts().effectType());
        assertTrue(mapped.facts().missingRequiredFields().isEmpty());
        assertEquals("remote_name", mapped.remoteToolName());
        assertEquals(Map.of("key", "value"), mapped.arguments());
        assertThrows(UnsupportedOperationException.class,
                () -> mapped.raw().put("riskLevel", "LOW"));
    }

    @Test
    void missingFieldsRemainVisibleAfterDomainNormalization() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("operationId", "op-2");
        raw.put("toolName", "validate");
        raw.put("adapterType", "MCP");
        raw.put("mcpId", "mcp-1");
        raw.put("arguments", Map.of());
        raw.put("resourceScope", "scope");
        raw.put("targetEnvironment", "test");
        raw.put("readOnly", true);
        raw.put("writesTargetResource", false);
        raw.put("requiresChangePackage", true);
        raw.put("requiresApproval", true);

        OpsPreApprovalOperationMapper.MappedOperation mapped =
                new OpsPreApprovalOperationMapper().map(List.of(raw)).get(0);

        assertTrue(mapped.facts().missingRequiredFields().contains("riskLevel"));
        assertTrue(mapped.facts().missingRequiredFields().contains("effectType"));
        assertTrue(mapped.facts().missingRequiredFields().contains("effectScope"));
        assertTrue(mapped.facts().missingRequiredFields().contains("mutability"));
        assertEquals("HIGH", mapped.facts().riskLevel());
        assertEquals("UNKNOWN", mapped.facts().effectType());
        assertFalse(mapped.facts().writesTargetResource());
    }
}
