package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsRuntimeToolContributorRegistryTest {

    @Test
    void contributorsRunInStableOrderThenId() {
        List<String> calls = new ArrayList<>();
        OpsRuntimeToolContributorRegistry registry = registry(List.of(
                contributor("optional-z", 250, OpsRuntimeToolContributorRequirement.OPTIONAL,
                        context -> calls.add("optional-z")),
                contributor("channel", 400, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> calls.add("channel")),
                contributor("repair", 100, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> calls.add("repair")),
                contributor("inspection-task", 300, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> calls.add("inspection-task")),
                contributor("change-package", 200, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> calls.add("change-package")),
                contributor("optional-a", 250, OpsRuntimeToolContributorRequirement.OPTIONAL,
                        context -> calls.add("optional-a"))));

        registry.contribute(context());

        assertEquals(List.of(
                "repair", "change-package", "optional-a", "optional-z", "inspection-task", "channel"), calls);
        assertEquals(List.of(
                "repair", "change-package", "optional-a", "optional-z", "inspection-task", "channel"),
                registry.orderedContributorIds());
    }

    @Test
    void duplicateNormalizedIdFailsAtStartup() {
        List<OpsRuntimeToolContributor> contributors = criticalContributors();
        contributors.add(contributor(" REPAIR ", 500, OpsRuntimeToolContributorRequirement.OPTIONAL,
                context -> { }));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsRuntimeToolContributorRegistry(contributors));

        assertTrue(error.getMessage().contains("RUNTIME_TOOL_CONTRIBUTOR_DUPLICATE"));
        assertTrue(error.getMessage().contains("repair"));
    }

    @Test
    void missingCriticalContributorFailsClosed() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsRuntimeToolContributorRegistry(List.of(
                        contributor("repair", 100, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }),
                        contributor("change-package", 200, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }),
                        contributor("inspection-task", 300, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }))));

        assertTrue(error.getMessage().contains("RUNTIME_CRITICAL_TOOL_CONTRIBUTOR_MISSING"));
        assertTrue(error.getMessage().contains("channel"));
    }

    @Test
    void criticalContributorMustDeclareRequired() {
        List<OpsRuntimeToolContributor> contributors = criticalContributors();
        contributors.removeIf(item -> item.id().equals("repair"));
        contributors.add(contributor("repair", 100, OpsRuntimeToolContributorRequirement.OPTIONAL,
                context -> { }));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsRuntimeToolContributorRegistry(contributors));

        assertEquals("RUNTIME_CRITICAL_TOOL_CONTRIBUTOR_NOT_REQUIRED：repair", error.getMessage());
    }

    @Test
    void optionalContributorMayBeAbsentOrPresentExplicitly() {
        OpsRuntimeToolContributorRegistry withoutOptional = registry(criticalContributors());
        OpsRuntimeToolContributor optional = contributor(
                "diagnostic", 350, OpsRuntimeToolContributorRequirement.OPTIONAL,
                context -> context.getMetadata().put("diagnosticEnabled", true));
        List<OpsRuntimeToolContributor> with = criticalContributors();
        with.add(optional);
        OpsRuntimeToolContributorRegistry withOptional = registry(with);
        OpsRuntimeResourceContext context = context();

        withoutOptional.contribute(context());
        withOptional.contribute(context);

        assertSame(optional, withOptional.require(" DIAGNOSTIC "));
        assertEquals(true, context.getMetadata().get("diagnosticEnabled"));
    }

    @Test
    void contributorFailurePropagatesAndStopsLaterContributors() {
        RuntimeException expected = new RuntimeException("CONTRIBUTOR_FAILURE");
        List<String> calls = new ArrayList<>();
        OpsRuntimeToolContributorRegistry registry = registry(List.of(
                contributor("repair", 100, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> calls.add("repair")),
                contributor("change-package", 200, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> { throw expected; }),
                contributor("inspection-task", 300, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> calls.add("inspection")),
                contributor("channel", 400, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> calls.add("channel"))));

        assertSame(expected, assertThrows(RuntimeException.class,
                () -> registry.contribute(context())));
        assertEquals(List.of("repair"), calls);
    }

    @Test
    void duplicateToolNameFailsClosed() {
        ToolCallback first = tool("same-tool");
        ToolCallback second = tool("same-tool");
        OpsRuntimeToolContributorRegistry registry = registry(List.of(
                contributor("repair", 100, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> context.getTools().add(first)),
                contributor("change-package", 200, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> context.getTools().add(second)),
                contributor("inspection-task", 300, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }),
                contributor("channel", 400, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { })));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry.contribute(context()));

        assertEquals("RUNTIME_TOOL_NAME_DUPLICATE：same-tool", error.getMessage());
    }

    @Test
    void preexistingMetadataCannotBeOverwrittenOrRemoved() {
        OpsRuntimeToolContributorRegistry overwrite = registry(List.of(
                contributor("repair", 100, OpsRuntimeToolContributorRequirement.REQUIRED,
                        context -> context.getMetadata().put("owner", "changed")),
                contributor("change-package", 200, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }),
                contributor("inspection-task", 300, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }),
                contributor("channel", 400, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { })));
        OpsRuntimeResourceContext context = context();
        context.getMetadata().put("owner", "original");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> overwrite.contribute(context));

        assertEquals("RUNTIME_TOOL_METADATA_OVERWRITE：repair：owner", error.getMessage());
    }

    private OpsRuntimeToolContributorRegistry registry(List<OpsRuntimeToolContributor> contributors) {
        return new OpsRuntimeToolContributorRegistry(contributors);
    }

    private List<OpsRuntimeToolContributor> criticalContributors() {
        return new ArrayList<>(List.of(
                contributor("repair", 100, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }),
                contributor("change-package", 200, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }),
                contributor("inspection-task", 300, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { }),
                contributor("channel", 400, OpsRuntimeToolContributorRequirement.REQUIRED, context -> { })));
    }

    private OpsRuntimeToolContributor contributor(String id,
                                                  int order,
                                                  OpsRuntimeToolContributorRequirement requirement,
                                                  java.util.function.Consumer<OpsRuntimeResourceContext> action) {
        return new OpsRuntimeToolContributor() {
            @Override
            public String id() { return id; }

            @Override
            public int order() { return order; }

            @Override
            public OpsRuntimeToolContributorRequirement requirement() { return requirement; }

            @Override
            public void contribute(OpsRuntimeResourceContext context) { action.accept(context); }
        };
    }

    private ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }

    private OpsRuntimeResourceContext context() {
        return OpsRuntimeResourceContext.builder()
                .tools(new ArrayList<>())
                .metadata(new LinkedHashMap<>())
                .events(new ArrayList<>())
                .executionTargetIds(new java.util.LinkedHashSet<>())
                .build();
    }
}
