package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentDefinitionSnapshotMapperTest {

    private final OpsAgentDefinitionSnapshotMapper mapper = new OpsAgentDefinitionSnapshotMapper();

    @Test
    void snapshotRoundTripPreservesPersistedIdentityAndDefinitionBody() {
        OpsAgentDefinition definition = definition();
        definition.setDefinitionHash(mapper.definitionHash(definition));

        AgentDefinitionSnapshot snapshot = mapper.toSnapshot(definition, true);
        OpsAgentDefinition restored = mapper.fromSnapshot(snapshot).orElseThrow();

        assertEquals("agent-a", snapshot.agentId());
        assertEquals(4, snapshot.version());
        assertEquals(AgentDefinitionLifecycle.VALIDATED, snapshot.lifecycle());
        assertTrue(snapshot.currentPublished());
        assertEquals("agent-a", restored.getAgentId());
        assertEquals(4, restored.getVersion());
        assertEquals("VALIDATED", restored.getLifecycle());
        assertEquals("project-a", restored.getProjectId());
        assertEquals("diagnose", restored.getNodes().get(0).getNodeId());
        assertEquals(snapshot.definitionHash(), restored.getDefinitionHash());
    }

    @Test
    void canonicalHashIgnoresPersistenceMetadataButDetectsDefinitionChanges() {
        OpsAgentDefinition definition = definition();
        String initial = mapper.definitionHash(definition);
        definition.setLifecycle("PUBLISHED");
        definition.setSource("YAML");
        definition.setDefinitionHash("stored-hash");

        assertEquals(initial, mapper.definitionHash(definition));

        definition.setInstruction("changed instruction");
        assertNotEquals(initial, mapper.definitionHash(definition));
    }

    @Test
    void copyIsDeepAndMalformedSnapshotIsIsolated() {
        OpsAgentDefinition definition = definition();
        OpsAgentDefinition copy = mapper.copy(definition);

        assertNotSame(definition, copy);
        assertNotSame(definition.getNodes(), copy.getNodes());
        copy.getNodes().get(0).getConfig().put("mode", "review");
        assertEquals("react", definition.getNodes().get(0).getConfig().get("mode"));

        AgentDefinitionSnapshot malformed = new AgentDefinitionSnapshot(
                "broken", 1, "hash", AgentDefinitionLifecycle.PUBLISHED,
                "broken", "", "CHAT", "", "", "start",
                "{not-json", true, true, "UI");
        assertFalse(mapper.fromSnapshot(malformed).isPresent());
    }

    private OpsAgentDefinition definition() {
        return OpsAgentDefinition.builder()
                .agentId("agent-a")
                .version(4)
                .lifecycle("VALIDATED")
                .name("Agent A")
                .projectId("project-a")
                .engine("GRAPH")
                .description("description")
                .instruction("instruction")
                .source("UI")
                .startNodeId("diagnose")
                .skills(List.of("diagnosis"))
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("diagnose")
                        .type("AGENT")
                        .mode("react")
                        .config(new java.util.LinkedHashMap<>(Map.of("mode", "react")))
                        .build()))
                .build();
    }
}
