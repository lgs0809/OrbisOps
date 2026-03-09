package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.contextbundle.service.RuntimeContextBundlePolicy;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextBundleMapperPolicyTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldAssembleEightLayerContextBundleWithStableMemoryRefs() {
        OpsRuntimeContextBundleMapper mapper = new OpsRuntimeContextBundleMapper();
        RuntimeContextBundlePolicy policy = new RuntimeContextBundlePolicy();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .sessionId("session-1")
                .runId("run-1")
                .userId("alice")
                .projectId("demo-project")
                .agentDefinitionId("generic-ops-react-agent")
                .agentVersion(3)
                .query("排查支付 5xx")
                .build();

        Map<String, Object> bundle = policy.assembleBase(mapper.layerInput(
                request,
                "用户偏好：先给结论。\n项目术语：示例订单。",
                Map.of(
                        "scene", "OPS_TROUBLESHOOTING",
                        "serviceId", "order-service",
                        "environment", "prod",
                        "selectedSkills", List.of("payment-sop"),
                        "toolResultRefs", List.of("tr-1"),
                        "trustedProofRefs", List.of("proof-1"),
                        "packageId", "cp-1",
                        "packageStatus", "DRAFT")));

        Map<String, Object> layers = (Map<String, Object>) bundle.get("layers");
        assertEquals(8, layers.size());
        assertTrue(layers.containsKey("taskContext"));
        assertTrue(layers.containsKey("conversationContext"));
        assertTrue(layers.containsKey("memoryContext"));
        assertTrue(layers.containsKey("projectRuntimeContext"));
        assertTrue(layers.containsKey("toolObservationContext"));
        assertTrue(layers.containsKey("skillContext"));
        assertTrue(layers.containsKey("policyContext"));
        assertTrue(layers.containsKey("changePackageContext"));
        assertFalse(String.valueOf(bundle.get("contextBundleId")).isBlank());
        assertEquals("run-1", bundle.get("runId"));
        assertEquals(3, bundle.get("agentVersion"));
        assertFalse(String.valueOf(bundle.get("createdAt")).isBlank());
        assertFalse(String.valueOf(bundle.get("memoryContextHash")).isBlank());
        assertEquals("context-bundle-v1", bundle.get("memoryInjectionVersion"));
        assertEquals(List.of("session:session-1", "project:demo-project"), bundle.get("memoryContextRefs"));
        assertTrue(String.valueOf(((Map<String, Object>) layers.get("projectRuntimeContext")).get("serviceId"))
                .contains("order-service"));
        assertEquals(List.of("tr-1"), ((Map<String, Object>) layers.get("toolObservationContext")).get("toolResultRefs"));
        assertEquals(List.of("payment-sop"), ((Map<String, Object>) layers.get("skillContext")).get("selected"));
    }
}
