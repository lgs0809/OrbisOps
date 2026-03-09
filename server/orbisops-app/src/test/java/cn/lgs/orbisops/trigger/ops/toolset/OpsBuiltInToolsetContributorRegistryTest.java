package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsBuiltInToolsetContributorRegistryTest {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Test
    void contributorsAreOrderedByOrderThenNormalizedId() {
        OpsBuiltInToolsetContributorRegistry registry = new OpsBuiltInToolsetContributorRegistry(List.of(
                contributor("zeta", 200, toolset("zeta.toolset", "zeta_tool")),
                contributor(" Beta ", 100, toolset("beta.toolset", "beta_tool")),
                contributor("alpha", 100, toolset("alpha.toolset", "alpha_tool"))));

        assertEquals(List.of("alpha", "beta", "zeta"), registry.orderedContributorIds());
        assertEquals(List.of("alpha.toolset", "beta.toolset", "zeta.toolset"),
                registry.definitions().stream().map(OpsToolsetDefinition::getToolsetId).toList());
    }

    @Test
    void duplicateContributorIdFailsAtStartup() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsBuiltInToolsetContributorRegistry(List.of(
                        contributor("same", 100, toolset("one", "one")),
                        contributor(" SAME ", 200, toolset("two", "two")))));

        assertEquals("BUILT_IN_TOOLSET_CONTRIBUTOR_DUPLICATE:same", error.getMessage());
    }

    @Test
    void duplicateToolsetIdAcrossContributorsFailsAtStartup() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsBuiltInToolsetContributorRegistry(List.of(
                        contributor("one", 100, toolset("shared", "one")),
                        contributor("two", 200, toolset("shared", "two")))));

        assertEquals("BUILT_IN_TOOLSET_ID_DUPLICATE:shared", error.getMessage());
    }

    @Test
    void duplicateToolNameInsideToolsetFailsAtStartup() {
        OpsToolDefinition tool = definitions.tools().read("same", "same");
        OpsToolsetDefinition duplicate = definitions.toolset(
                "duplicate.tools", "duplicate", "duplicate", "MCP", true,
                List.of(tool, definitions.tools().read("same", "same again")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsBuiltInToolsetContributorRegistry(List.of(
                        contributor("duplicate", 100, duplicate))));

        assertEquals("BUILT_IN_TOOL_NAME_DUPLICATE:duplicate.tools:same", error.getMessage());
    }

    @Test
    void dependencyCycleFailsAtStartup() {
        OpsToolsetDefinition first = toolset("first", "first_tool");
        OpsToolsetDefinition second = toolset("second", "second_tool");
        first.setPrerequisites("second");
        second.setPrerequisites("first");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsBuiltInToolsetContributorRegistry(List.of(
                        contributor("cycle", 100, first, second))));

        assertTrue(error.getMessage().startsWith("BUILT_IN_TOOLSET_DEPENDENCY_CYCLE:"));
    }

    @Test
    void unknownDependencyFailsAtStartup() {
        OpsToolsetDefinition definition = toolset("known", "known_tool");
        definition.setPrerequisites("missing");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsBuiltInToolsetContributorRegistry(List.of(
                        contributor("unknown", 100, definition))));

        assertEquals("BUILT_IN_TOOLSET_DEPENDENCY_UNKNOWN:known:missing", error.getMessage());
    }

    @Test
    void returnedDefinitionsAreDeepCopies() {
        OpsBuiltInToolsetContributorRegistry registry = new OpsBuiltInToolsetContributorRegistry(List.of(
                contributor("copy", 100, toolset("copy.toolset", "copy_tool"))));
        List<OpsToolsetDefinition> first = registry.definitions();
        first.get(0).setName("mutated");
        first.get(0).getTools().get(0).setToolName("mutated_tool");
        List<OpsToolsetDefinition> second = registry.definitions();

        assertNotEquals("mutated", second.get(0).getName());
        assertEquals("copy_tool", second.get(0).getTools().get(0).getToolName());
        assertThrows(UnsupportedOperationException.class,
                () -> second.add(toolset("illegal", "illegal")));
    }

    @Test
    void coreContributorRegistryPreservesAllTwentySixDefinitions() {
        OpsBuiltInToolsetContributorRegistry registry = new OpsBuiltInToolsetContributorRegistry(List.of(
                new ObservabilityToolsetContributor(),
                new DatabaseToolsetContributor(),
                new CacheToolsetContributor(),
                new ContainerToolsetContributor(),
                new PlatformToolsetContributor(),
                new RepairToolsetContributor(),
                new ChangePackageToolsetContributor(),
                new InspectionToolsetContributor(),
                new ChannelToolsetContributor(),
                new SkillToolsetContributor()));

        assertEquals(26, registry.definitions().size());
        assertEquals(List.of(
                "observability", "database", "cache", "container", "platform",
                "repair", "change-package", "inspection", "channel", "skill"),
                registry.orderedContributorIds());
    }

    private OpsToolsetDefinition toolset(String id, String toolName) {
        return definitions.toolset(
                id, id, id, "MCP", true,
                List.of(definitions.tools().read(toolName, toolName)));
    }

    private OpsBuiltInToolsetContributor contributor(
            String id,
            int order,
            OpsToolsetDefinition... toolsets) {
        List<OpsToolsetDefinition> definitions = new ArrayList<>(List.of(toolsets));
        return new OpsBuiltInToolsetContributor() {
            @Override
            public String contributorId() {
                return id;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public List<OpsToolsetDefinition> definitions() {
                return definitions;
            }
        };
    }
}
