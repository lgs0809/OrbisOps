package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolsetDefinitionCopierTest {

    @Test
    void copyMustPreserveAllFieldsAndBreakMutableAliases() {
        OpsToolDefinition tool = OpsToolDefinition.builder()
                .toolName("custom_tool")
                .displayName("Custom Tool")
                .description("description")
                .parametersJson("{\"type\":\"object\"}")
                .adapterType("HTTP_API")
                .commandTemplate("template")
                .mcpServerId("mcp-1")
                .remoteToolName("remote")
                .httpConfigJson("{\"url\":\"http://example\"}")
                .dbConfigJson("{\"database\":\"ops\"}")
                .readOnly(false)
                .writesRepairWorkspace(true)
                .writesTargetResource(true)
                .requiresChangePackage(true)
                .requiresApproval(true)
                .riskLevel("HIGH")
                .outputBudgetJson("{\"max\":100}")
                .enabled(true)
                .build();
        OpsToolsetDefinition source = OpsToolsetDefinition.builder()
                .toolsetId("custom.toolset")
                .name("Custom")
                .description("description")
                .prerequisites("ready")
                .tags(new ArrayList<>(List.of("custom", "ops")))
                .sourceType("CUSTOM")
                .adapterType("HTTP_API")
                .enabled(true)
                .readOnlyDefault(false)
                .tools(new ArrayList<>(List.of(tool)))
                .createBy("creator")
                .updateBy("updater")
                .createTime("create-time")
                .updateTime("update-time")
                .build();

        OpsToolsetDefinition copy = new OpsToolsetDefinitionCopier().copy(source);

        assertEquals(source, copy);
        assertNotSame(source, copy);
        assertNotSame(source.getTags(), copy.getTags());
        assertNotSame(source.getTools(), copy.getTools());
        assertNotSame(source.getTools().get(0), copy.getTools().get(0));

        copy.setName("changed");
        copy.getTags().add("changed");
        copy.getTools().get(0).setToolName("changed_tool");

        assertEquals("Custom", source.getName());
        assertEquals(List.of("custom", "ops"), source.getTags());
        assertEquals("custom_tool", source.getTools().get(0).getToolName());
    }

    @Test
    void copyMustNormalizeNullCollectionsToEmptyLists() {
        OpsToolsetDefinition source = OpsToolsetDefinition.builder()
                .toolsetId("empty")
                .tags(null)
                .tools(null)
                .build();

        OpsToolsetDefinition copy = new OpsToolsetDefinitionCopier().copy(source);

        assertTrue(copy.getTags().isEmpty());
        assertTrue(copy.getTools().isEmpty());
    }
}
