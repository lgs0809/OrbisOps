package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolsetRegistryTest {

    @Test
    void builtInListsMustBeDeepCopiesOfCanonicalCatalog() {
        OpsToolsetRegistry registry = new OpsToolsetRegistry();

        List<OpsToolsetDefinition> first = registry.listBuiltInToolsets();
        List<OpsToolsetDefinition> second = registry.listBuiltInToolsets();

        assertEquals(registry.rawBuiltInToolsets().size(), first.size());
        assertEquals(first, second);
        assertNotSame(first.get(0), second.get(0));
        assertNotSame(first.get(0).getTags(), second.get(0).getTags());
        assertNotSame(first.get(0).getTools().get(0), second.get(0).getTools().get(0));

        first.get(0).setName("changed");
        first.get(0).getTags().add("changed");
        first.get(0).getTools().get(0).setToolName("changed_tool");

        List<OpsToolsetDefinition> third = registry.listBuiltInToolsets();
        assertEquals("Prometheus 查询", third.get(0).getName());
        assertEquals(List.of("observability"), third.get(0).getTags());
        assertEquals("prometheus_instant_query",
                third.get(0).getTools().get(0).getToolName());
    }

    @Test
    void effectiveListMustAppendCopiedEnabledCustomToolsetsAndDropDisabledItems() {
        OpsToolsetRegistry registry = new OpsToolsetRegistry();
        OpsToolsetDefinition enabled = custom("custom.enabled", true, "enabled_tool");
        OpsToolsetDefinition disabled = custom("custom.disabled", false, "disabled_tool");

        List<OpsToolsetDefinition> effective = registry.listEffectiveToolsets(
                "project-1",
                new ArrayList<>(List.of(enabled, disabled)));

        assertEquals(registry.listBuiltInToolsets().size() + 1, effective.size());
        assertTrue(effective.stream().anyMatch(item ->
                "custom.enabled".equals(item.getToolsetId())));
        assertFalse(effective.stream().anyMatch(item ->
                "custom.disabled".equals(item.getToolsetId())));
        OpsToolsetDefinition copied = effective.stream()
                .filter(item -> "custom.enabled".equals(item.getToolsetId()))
                .findFirst()
                .orElseThrow();
        assertNotSame(enabled, copied);
        assertNotSame(enabled.getTools().get(0), copied.getTools().get(0));
    }

    @Test
    void findToolMustMatchExactToolsetAndToolNames() {
        OpsToolsetRegistry registry = new OpsToolsetRegistry();
        List<OpsToolsetDefinition> builtIns = registry.listBuiltInToolsets();

        assertTrue(registry.findTool(
                builtIns,
                "db.mysql.readonly",
                "mysql_query_readonly").isPresent());
        assertFalse(registry.findTool(
                builtIns,
                "db.mysql.readonly",
                "MYSQL_QUERY_READONLY").isPresent());
        assertFalse(registry.findTool(
                builtIns,
                "missing",
                "mysql_query_readonly").isPresent());
        assertFalse(registry.findTool(
                builtIns,
                null,
                null).isPresent());
    }

    private OpsToolsetDefinition custom(
            String id,
            boolean enabled,
            String toolName) {
        return OpsToolsetDefinition.builder()
                .toolsetId(id)
                .name(id)
                .sourceType("CUSTOM")
                .adapterType("HTTP_API")
                .enabled(enabled)
                .readOnlyDefault(true)
                .tags(new ArrayList<>(List.of("custom")))
                .tools(new ArrayList<>(List.of(
                        OpsToolDefinition.builder()
                                .toolName(toolName)
                                .adapterType("HTTP_API")
                                .readOnly(true)
                                .riskLevel("LOW")
                                .enabled(true)
                                .build())))
                .build();
    }
}
