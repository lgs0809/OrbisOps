package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionMemoryCatalogTest {

    private final AgentDefinitionMemoryCatalog<TestDefinition> catalog =
            new AgentDefinitionMemoryCatalog<>(new TestDescriptor());

    @Test
    void keepsCurrentAndVersionCatalogsWithDescendingVersionOrder() {
        catalog.register(definition("agent-a", 1, "first"), true);
        catalog.register(definition("agent-a", 2, "draft"), false);
        catalog.register(definition("agent-a", 3, "current"), true);

        assertEquals("current", catalog.current("agent-a").name);
        assertEquals(List.of(3, 2, 1), catalog.versions("agent-a").stream()
                .map(definition -> definition.version)
                .toList());
        assertEquals(3, catalog.maxVersion("agent-a"));
        assertEquals(3, catalog.currentVersion("agent-a"));
        assertEquals(1, catalog.currentSize());
        assertTrue(catalog.containsCurrent("agent-a"));
        assertTrue(catalog.containsVersion("agent-a", 2));
    }

    @Test
    void returnsDefensiveSnapshotsAndSupportsCurrentRemovalAndClear() {
        TestDefinition original = definition("agent-a", 1, "stored");
        catalog.register(original, true);
        original.name = "caller-mutated";

        TestDefinition current = catalog.current("agent-a");
        current.name = "read-mutated";

        assertEquals("stored", catalog.current("agent-a").name);
        assertEquals("stored", catalog.version("agent-a", 1).name);
        assertEquals("stored", catalog.currentDefinitions().get(0).name);

        TestDefinition removed = catalog.removeCurrent("agent-a");
        removed.name = "removed-mutated";
        assertFalse(catalog.containsCurrent("agent-a"));
        assertNull(catalog.current("agent-a"));
        assertTrue(catalog.containsVersion("agent-a", 1));

        catalog.clear();
        assertTrue(catalog.versions("agent-a").isEmpty());
        assertEquals(0, catalog.maxVersion("agent-a"));
    }

    private TestDefinition definition(String agentId, int version, String name) {
        return new TestDefinition(agentId, version, name, AgentDefinitionLifecycle.PUBLISHED);
    }

    private static final class TestDescriptor
            implements AgentDefinitionDescriptorPort<TestDefinition> {

        @Override
        public AgentDefinitionVersionState describe(TestDefinition definition) {
            return new AgentDefinitionVersionState(
                    definition.agentId,
                    definition.version,
                    "hash-" + definition.version,
                    "",
                    definition.lifecycle);
        }

        @Override
        public TestDefinition snapshot(TestDefinition definition) {
            return new TestDefinition(
                    definition.agentId,
                    definition.version,
                    definition.name,
                    definition.lifecycle);
        }
    }

    private static final class TestDefinition {
        private final String agentId;
        private final int version;
        private String name;
        private final AgentDefinitionLifecycle lifecycle;

        private TestDefinition(String agentId,
                               int version,
                               String name,
                               AgentDefinitionLifecycle lifecycle) {
            this.agentId = agentId;
            this.version = version;
            this.name = name;
            this.lifecycle = lifecycle;
        }
    }
}
