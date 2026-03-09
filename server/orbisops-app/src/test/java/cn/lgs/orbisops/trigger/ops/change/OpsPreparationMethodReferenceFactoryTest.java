package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OpsPreparationMethodReferenceFactoryTest {

    private final OpsPreparationMethodReferenceFactory factory =
            new OpsPreparationMethodReferenceFactory();

    @Test
    void projectAgentProducesFrozenDeterministicMethodReference() {
        OpsAgentDefinition agent = agent("prep-1", "project-1", 3);
        Map<String, Object> firstRequest = new LinkedHashMap<>();
        firstRequest.put("allowedTools", List.of("mcp-1", "query_health"));
        firstRequest.put("verificationCriteria", List.of("latency below threshold"));
        firstRequest.put("preparationMethodSummary", "prepare health remediation");
        Map<String, Object> secondRequest = new LinkedHashMap<>();
        secondRequest.put("preparationMethodSummary", "prepare health remediation");
        secondRequest.put("verificationCriteria", List.of("latency below threshold"));
        secondRequest.put("allowedTools", List.of("mcp-1", "query_health"));

        Map<String, Object> first = factory.create(firstRequest, agent);
        Map<String, Object> second = factory.create(secondRequest, agent);

        assertEquals("PROJECT", first.get("scopeType"));
        assertEquals("project-1", first.get("projectId"));
        assertEquals(3, first.get("preparationAgentVersion"));
        assertEquals(first.get("preparationMethodHash"), second.get("preparationMethodHash"));
        assertEquals(
                CanonicalObjectHasher.sha256(first, Set.of("preparationMethodHash")),
                first.get("preparationMethodHash"));
    }

    @Test
    void globalAgentAndMeaningfulMethodChangeProduceExpectedIdentity() {
        OpsAgentDefinition global = agent("default-prep", "", 1);
        Map<String, Object> baseline = factory.create(Map.of(), global);
        Map<String, Object> changed = factory.create(
                Map.of("preparationMethodSummary", "different method"),
                global);

        assertEquals("GLOBAL", baseline.get("scopeType"));
        assertEquals("", baseline.get("projectId"));
        assertNotEquals(
                baseline.get("preparationMethodHash"),
                changed.get("preparationMethodHash"));
    }

    private OpsAgentDefinition agent(String id, String projectId, int version) {
        return OpsAgentDefinition.builder()
                .agentId(id)
                .version(version)
                .name("Preparation Agent")
                .projectId(projectId)
                .build();
    }
}
