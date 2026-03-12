package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRemoteCallPolicyArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void assessmentMustRemainTypedSecurityAndAuditContext() throws IOException {
        String assessment = read(RUNTIME + "OpsMcpRemoteCallAssessment.java");

        assertAll(
                () -> assertTrue(assessment.contains("record OpsMcpRemoteCallAssessment")),
                () -> assertTrue(assessment.contains("OpsToolCallStage stage")),
                () -> assertTrue(assessment.contains("Map<String, Object> argumentPolicy")),
                () -> assertTrue(assessment.contains("platformPolicyApproved")),
                () -> assertTrue(assessment.contains("callbackPolicyRequired")),
                () -> assertTrue(assessment.contains("approvedLandingContext")),
                () -> assertTrue(assessment.contains("auditMetadata")),
                () -> assertFalse(assessment.contains("ObjectProvider")),
                () -> assertFalse(assessment.contains("@Component")),
                () -> assertTrue(assessment.lines().count() < 110));
    }

    @Test
    void remoteCallPolicyMustOwnStageEffectArgumentAndChangePackageRules() throws IOException {
        String policy = read(RUNTIME + "OpsMcpRemoteCallPolicy.java");

        assertAll(
                () -> assertTrue(policy.contains("OpsMcpRemoteCallAssessment assess")),
                () -> assertTrue(policy.contains("void assertAllowed")),
                () -> assertTrue(policy.contains("OpsMcpToolArgumentPolicyChecker")),
                () -> assertTrue(policy.contains("MCP_POLICY_PENDING_REVIEW")),
                () -> assertTrue(policy.contains("MCP_TOOL_EFFECT_UNKNOWN")),
                () -> assertTrue(policy.contains("MCP_STAGE_NOT_ALLOWED")),
                () -> assertTrue(policy.contains("MCP_TOOL_REQUIRES_CHANGE_PACKAGE")),
                () -> assertFalse(policy.contains("MCP_TOOL_REQUIRES_HUMAN_REVIEW")),
                () -> assertFalse(policy.contains("ProgressiveMcpProcessManager")),
                () -> assertFalse(policy.contains("OpsMcpClientRegistry")),
                () -> assertFalse(policy.contains("ToolCallback")),
                () -> assertFalse(policy.contains("ObjectProvider")),
                () -> assertTrue(policy.lines().count() < 300));
    }

    @Test
    void providerMustOnlyOrchestrateTypedAssessmentAndRemoteInvocation() throws IOException {
        String progressive = read(RUNTIME + "OpsProgressiveMcpInvocationService.java");
        String provider = read(RUNTIME + "OpsMcpToolProvider.java");

        assertAll(
                () -> assertTrue(progressive.contains("private final OpsMcpRemoteCallPolicy remoteCallPolicy;")),
                () -> assertTrue(progressive.contains("OpsMcpRemoteCallAssessment assessment = remoteCallPolicy.assess(")),
                () -> assertTrue(progressive.contains("callAuditMetadata.putAll(assessment.auditMetadata())")),
                () -> assertTrue(progressive.contains("remoteCallPolicy.assertAllowed(assessment, remoteArgs)")),
                () -> assertTrue(progressive.contains("assessment.callbackPolicyRequired()")),
                () -> assertFalse(progressive.contains("OpsMcpToolArgumentPolicyChecker")),
                () -> assertFalse(progressive.contains("platformPolicyApprovedForRemoteCall")),
                () -> assertFalse(progressive.contains("hasApprovedLandingContextFields")),
                () -> assertFalse(progressive.contains("directMcpCallMetadata")),
                () -> assertFalse(progressive.contains("assertDirectMcpCallAllowed")),
                () -> assertFalse(progressive.contains("assertStageAllowed")),
                () -> assertFalse(progressive.contains("policyErrorCode")),
                () -> assertFalse(progressive.contains("approvedLandingContext")),
                () -> assertFalse(progressive.contains("mutatingActions")),
                () -> assertFalse(progressive.contains("objectMap")),
                () -> assertFalse(provider.contains("private final OpsMcpRemoteCallPolicy")),
                () -> assertTrue(progressive.lines().count() < 180));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
