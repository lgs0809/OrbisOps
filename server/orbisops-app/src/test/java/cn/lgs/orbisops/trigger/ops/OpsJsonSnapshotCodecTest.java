package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpsJsonSnapshotCodecTest {

    @Test
    void shouldPersistSharedCollectionsWithoutReferenceMarkers() {
        List<String> sharedEmptyList = List.of();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("payment-ops-agent")
                .projectId("payment")
                .mcpIds(sharedEmptyList)
                .skills(sharedEmptyList)
                .nodes(List.of(
                        OpsWorkflowNode.builder()
                                .nodeId("investigate")
                                .type("AGENT")
                                .mcpIds(sharedEmptyList)
                                .skills(sharedEmptyList)
                                .build()))
                .build();

        String json = OpsJsonSnapshotCodec.write(definition);
        OpsAgentDefinition restored = OpsJsonSnapshotCodec.read(json, OpsAgentDefinition.class);

        assertFalse(json.contains("$ref"));
        assertNotNull(restored);
        assertEquals("payment", restored.getProjectId());
        assertEquals(1, restored.getNodes().size());
        assertEquals(List.of(), restored.getNodes().get(0).getMcpIds());
    }
}
