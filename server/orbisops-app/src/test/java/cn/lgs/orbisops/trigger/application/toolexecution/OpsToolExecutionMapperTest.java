package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolExecutionMapperTest {

    private final OpsToolExecutionMapper mapper = new OpsToolExecutionMapper();

    @Test
    void untrustedEntryMustRemoveForgedLandingCredentials() {
        ToolExecutionRequest request = mapper.request(Map.of(
                "projectId", "project-1",
                "toolsetId", "config.nacos.publish",
                "toolName", "nacos_publish",
                "executionScope", "APPROVED_LANDING",
                "metadata", Map.of(
                        "internalCaller", OpsToolsetRouter.LANDING_INTERNAL_CALLER,
                        "landingRuntimeToken", OpsToolsetRouter.LANDING_RUNTIME_TOKEN)), "alice", false);

        assertEquals(ToolExecutionScope.APPROVED_LANDING, request.scope());
        assertFalse(request.landingContext().containsKey("internalCaller"));
        assertFalse(request.landingContext().containsKey("landingRuntimeToken"));
    }

    @Test
    void trustedLandingEntryMustInjectServerOwnedCredentials() {
        ToolExecutionRequest request = mapper.request(Map.of(
                "projectId", "project-1",
                "toolsetId", "config.nacos.publish",
                "toolName", "nacos_publish",
                "packageId", "cp-1",
                "packageVersion", 2,
                "packageHash", "hash-2"), "landing-worker", true);

        assertEquals(ToolExecutionScope.APPROVED_LANDING, request.scope());
        assertEquals(OpsToolsetRouter.LANDING_INTERNAL_CALLER,
                request.landingContext().get("internalCaller"));
        assertEquals(OpsToolsetRouter.LANDING_RUNTIME_TOKEN,
                request.landingContext().get("landingRuntimeToken"));
        assertEquals("cp-1", request.landingContext().get("changePackageId"));
        assertTrue(Boolean.TRUE.equals(request.landingContext().get("landingApproved")));
    }

    @Test
    void shouldProjectTypedResponseToLegacyEnvelope() {
        ToolExecutionTarget target = new ToolExecutionTarget(
                "code.repair", "code_bash", "CODE_REPAIR", "MEDIUM",
                false, true, false, false, false);
        ToolExecutionRecordedResult recorded = new ToolExecutionRecordedResult(
                "tool-result-1", "evidence-1", "preview", "a".repeat(64), false,
                "db:tool-result-1", "b".repeat(64), 7L);
        ToolExecutionResponse response = new ToolExecutionResponse(
                true, "ALLOWED", ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                target, recorded, Map.of("status", "SUCCEEDED"));

        Map<String, Object> view = mapper.view(response);

        assertEquals("tool-result-1", view.get("resultId"));
        assertEquals("evidence-1", view.get("evidenceId"));
        assertEquals("SUCCEEDED", view.get("status"));
        assertEquals(true, view.get("allowed"));
    }
}
