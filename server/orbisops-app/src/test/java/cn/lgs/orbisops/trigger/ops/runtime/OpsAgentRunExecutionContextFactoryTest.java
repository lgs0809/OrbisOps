package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStyle;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentRunExecutionContextFactoryTest {

    private final OpsAgentRunExecutionContextFactory factory = new OpsAgentRunExecutionContextFactory();

    @Test
    void defaultChatIsReactPrepareWithTestFullProfile() {
        OpsAgentChatRequest request = request("AGENT");

        var context = factory.bindServerContext(request, definition("agent-1"));

        assertEquals(TriggerSource.CHAT, context.triggerSource());
        assertEquals(AgentExecutionStyle.REACT, context.executionStyle());
        assertEquals(AgentExecutionStage.PREPARE, context.stage());
        assertEquals(CapabilityProfile.TEST_FULL, context.capabilityProfile());
    }

    @Test
    void projectDefaultCannotMasqueradeSpecializedWorkflowAsReact() {
        OpsAgentChatRequest request = request("AGENT");
        OpsAgentDefinition workflow = definition("workflow-as-default");
        workflow.setDefinitionKind("SPECIALIZED_WORKFLOW");

        SecurityException error = assertThrows(SecurityException.class,
                () -> factory.bindServerContext(request, workflow));

        assertEquals("DEFAULT_REACT_AGENT_REQUIRED", error.getMessage());
    }

    @Test
    void manuallySelectedDragDropDefinitionIsWorkflowButKeepsSameChatRuntimeAuthority() {
        OpsAgentChatRequest request = request("WORKFLOW");

        var context = factory.bindServerContext(request, definition("workflow-1"));

        assertEquals(AgentExecutionStyle.WORKFLOW, context.executionStyle());
        assertEquals(AgentExecutionStage.PREPARE, context.stage());
        assertEquals(CapabilityProfile.TEST_FULL, context.capabilityProfile());
    }

    @Test
    void inspectionUsesReadOnlyProductionRuntimeIndependentOfExecutionStyle() {
        OpsAgentChatRequest request = request("WORKFLOW");
        request.getMetadata().put("_trustedTriggerSource", TriggerSource.INSPECTION);

        var context = factory.bindServerContext(request, definition("inspection-workflow"));

        assertEquals(TriggerSource.INSPECTION, context.triggerSource());
        assertEquals(AgentExecutionStyle.WORKFLOW, context.executionStyle());
        assertEquals(AgentExecutionStage.INVESTIGATE, context.stage());
        assertEquals(CapabilityProfile.PROD_DIAGNOSTIC, context.capabilityProfile());
    }

    @Test
    void landingRequiresApprovedPackageAndAlwaysUsesPlatformReactStyle() {
        OpsAgentChatRequest request = request("WORKFLOW");
        request.getMetadata().put("_trustedTriggerSource", TriggerSource.LANDING);
        ApprovedPackageSnapshot approved = new ApprovedPackageSnapshot(
                "cp-1", 1, "hash-1", "project-1", "prod", "", Instant.now().plusSeconds(60));
        request.getMetadata().put(OpsAgentRunExecutionContextFactory.TRUSTED_APPROVED_PACKAGE_KEY, approved);

        var context = factory.bindServerContext(
                request,
                new OpsPlatformLandingRuntimeDefinitionFactory().create("project-1"));

        assertEquals(AgentExecutionStage.LANDING, context.stage());
        assertEquals(AgentExecutionStyle.REACT, context.executionStyle());
        assertEquals(CapabilityProfile.PROD_FULL, context.capabilityProfile());
        assertTrue(context.approvedPackage().isPresent());
    }

    @Test
    void landingWithoutApprovedPackageFailsClosed() {
        OpsAgentChatRequest request = request("AGENT");
        request.getMetadata().put("_trustedTriggerSource", TriggerSource.LANDING);

        assertThrows(SecurityException.class,
                () -> factory.bindServerContext(request, definition("agent-1")));
    }

    private OpsAgentChatRequest request(String mode) {
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .projectId("project-1")
                .userId("alice")
                .mode(mode)
                .metadata(new LinkedHashMap<>())
                .build();
    }

    private OpsAgentDefinition definition(String id) {
        return OpsAgentDefinition.builder()
                .agentId(id)
                .version(1)
                .definitionHash("hash-" + id)
                .projectId("project-1")
                .engine(OpsUnifiedAgentEngineAdapter.KEY)
                .instruction("test")
                .capabilities(List.of())
                .mcpIds(List.of("mcp-test", "mcp-prod"))
                .executionTargetIds(List.of("target-1"))
                .nodes(List.of())
                .build();
    }
}
