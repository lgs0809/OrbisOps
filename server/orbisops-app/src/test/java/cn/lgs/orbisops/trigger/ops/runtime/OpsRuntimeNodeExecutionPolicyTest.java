package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRuntimeNodeExecutionPolicyTest {

    private final OpsRuntimeNodeExecutionPolicy policy =
            new OpsRuntimeNodeExecutionPolicy(new OpsAnalysisRoutingPolicy());

    @Test
    void nullAndNonAgentTypesNormalizeDirectly() {
        assertEquals("CHAT", policy.executionNodeType(null));
        assertEquals("SUB_AGENT", policy.executionNodeType(
                OpsWorkflowNode.builder().type("sub-agent").build()));
        assertEquals("KNOWLEDGE_RETRIEVAL", policy.executionNodeType(
                OpsWorkflowNode.builder().type("knowledge-retrieval").build()));
    }

    @Test
    void explicitAgentModeOverridesRoleInference() {
        assertEquals("PLAN", policy.executionNodeType(
                OpsWorkflowNode.builder().type("AGENT").mode("plan").agent("data-agent").build()));
        assertEquals("AGENTSCOPE", policy.executionNodeType(
                OpsWorkflowNode.builder().type("AGENT").mode("react").agent("report-agent").build()));
        OpsWorkflowNode legacyReview = OpsWorkflowNode.builder()
                .type("AGENT")
                .mode("review")
                .agent("ops-main-agent")
                .build();
        assertEquals("LLM", policy.normalizeAgentMode(legacyReview));
        assertEquals("AGENT", policy.executionNodeType(legacyReview));
    }

    @Test
    void configAgentModeIsUsedWhenDedicatedFieldIsBlank() {
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .type("AGENT")
                .config(Map.of("agentMode", " react "))
                .build();

        assertEquals("REACT", policy.normalizeAgentMode(node));
        assertEquals("AGENTSCOPE", policy.executionNodeType(node));
    }

    @Test
    void configuredAnalysisRolesMapToCanonicalNodeTypes() {
        assertEquals("REPORT", roleNodeType("reporter"));
        assertEquals("NOTIFY", roleNodeType("notifier"));
        assertEquals("PLAN", roleNodeType("main-planner"));
        assertEquals("AGENTSCOPE", roleNodeType("data-agent"));
        assertEquals("REVIEW", roleNodeType("reviewer"));
        assertEquals("AGENT", roleNodeType("general"));
    }

    @Test
    void legacyAgentNamesStillProvideFallbackClassification() {
        assertEquals("PLAN", policy.executionNodeType(
                OpsWorkflowNode.builder().type("AGENT").agent("ops-main-agent").build()));
        assertEquals("REPORT", policy.executionNodeType(
                OpsWorkflowNode.builder().type("AGENT").agent("final-report-agent").build()));
        assertEquals("NOTIFY", policy.executionNodeType(
                OpsWorkflowNode.builder().type("AGENT").agent("channel-notify-agent").build()));
    }

    @Test
    void firstTextUsesFirstNonBlankValueWithoutRewritingIt() {
        assertEquals("  selected  ", policy.firstText("", "  selected  ", "fallback"));
        assertEquals("", policy.firstText(null, " "));
    }

    @Test
    void runtimeCannotReclaimNodeInterpretationProtocolOrOrphanHelpers() {
        Set<String> forbiddenMethods = Set.of(
                "normalizeMode",
                "normalizeEngine",
                "normalizeType",
                "executionNodeType",
                "normalizeAgentMode",
                "configText",
                "intConfig",
                "stringConfigList",
                "safeName",
                "analysisAgentRole",
                "firstText");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
    }

    private String roleNodeType(String role) {
        return policy.executionNodeType(OpsWorkflowNode.builder()
                .type("AGENT")
                .config(Map.of("role", role))
                .build());
    }
}
