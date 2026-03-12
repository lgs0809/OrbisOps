package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAgentAuthorityArchitectureTest {

    private static final Path RUNTIME = Path.of(
            "../orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime");

    @Test
    void ordinaryAgentAuthorityMustStopAtPrepareChangeAndProdMutationMustRemainProposalOnly()
            throws IOException {
        String authority = read("OpsRuntimeAgentAuthority.java");
        String toolAuthority = read("OpsRuntimeToolAuthorityPolicy.java");
        String mcpResolver = read("OpsRuntimeMcpResolver.java");
        String unifiedMcp = read("OpsMcpUnifiedToolExecutor.java");
        String proposedAction = read("OpsMcpProposedActionFactory.java");
        String trace = read("OpsRuntimeToolTraceDecorator.java");

        assertAll(
                () -> assertTrue(authority.contains("OBSERVE_ONLY")),
                () -> assertTrue(authority.contains("PREPARE_CHANGE")),
                () -> assertTrue(authority.contains("AGENT_PROD_FULL_AUTHORITY_FORBIDDEN")),
                () -> assertTrue(authority.contains("execution.stage() == AgentExecutionStage.LANDING")),
                () -> assertTrue(toolAuthority.contains("PROPOSABLE_ONLY")),
                () -> assertTrue(toolAuthority.contains("remoteCallExecuted")),
                () -> assertTrue(toolAuthority.contains("PROPOSED_ACTION_RECORDED")),
                () -> assertTrue(toolAuthority.contains("runAuthority.stage() != AgentExecutionStage.LANDING")),
                () -> assertTrue(mcpResolver.contains("OpsRuntimeAgentAuthority.resolve(context)")),
                () -> assertTrue(mcpResolver.contains("case PREPARE_CHANGE -> CapabilityProfile.TEST_FULL")),
                () -> assertTrue(unifiedMcp.contains("OpsMcpProposedActionFactory")),
                () -> assertTrue(proposedAction.contains("productionResource(config)")),
                () -> assertTrue(proposedAction.contains("REQUIRES_CHANGE_PACKAGE")),
                () -> assertTrue(trace.contains("TOOL_CALL_PROPOSED")),
                () -> assertFalse(trace.contains("TOOL_CALL_PROPOSED\",\n                    \"SUCCEEDED\"")));
    }

    private String read(String name) throws IOException {
        return Files.readString(RUNTIME.resolve(name));
    }
}
