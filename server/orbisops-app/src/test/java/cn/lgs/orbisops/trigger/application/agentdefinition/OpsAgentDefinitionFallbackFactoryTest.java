package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.testsupport.OpsAgentDefinitionValidatorTestFactory;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentDefinitionFallbackFactoryTest {

    @Test
    void createsValidPlatformFallbackGraphWithoutRegistryDependency() {
        OpsAgentDefinition definition = new OpsAgentDefinitionFallbackFactory().create();
        OpsAgentDefinitionMutationAdapter adapter = new OpsAgentDefinitionMutationAdapter(
                OpsAgentDefinitionValidatorTestFactory.create(),
                new OpsAgentDefinitionSnapshotMapper(),
                null,
                null,
                null);

        adapter.normalize(definition);
        adapter.validate(definition);

        assertEquals(OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID, definition.getAgentId());
        assertEquals("HYBRID", definition.getEngine());
        assertEquals("start", definition.getStartNodeId());
        assertEquals(3, definition.getNodes().size());
        assertEquals(2, definition.getEdges().size());
        assertEquals("react", definition.getNodes().get(1).getMode());
        assertTrue(Boolean.TRUE.equals(definition.getNodes().get(1).getRagEnabled()));
        assertTrue(Boolean.TRUE.equals(definition.getNodes().get(1).getChangePackageEnabled()));
    }
}
