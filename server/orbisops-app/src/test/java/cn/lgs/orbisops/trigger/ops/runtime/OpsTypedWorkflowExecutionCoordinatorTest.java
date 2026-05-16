package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OpsTypedWorkflowExecutionCoordinatorTest {

    @Test
    void shadowModeMustRecordBindingFailureWithoutBlockingLegacyGraph() {
        OpsAgentDefinitionValidator validator = mock(OpsAgentDefinitionValidator.class);
        doThrow(new IllegalStateException("invalid typed definition"))
                .when(validator).compile(org.mockito.ArgumentMatchers.any());
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsTypedWorkflowExecutionCoordinator coordinator = coordinator(
                validator, new OpsTypedWorkflowSettings("SHADOW", 3));

        assertDoesNotThrow(() -> coordinator.begin(
                definition(), durableRequest(), definition().getNodes(), events, null));

        assertEquals("TYPED_WORKFLOW_FAILED", events.get(events.size() - 1).getEventType());
        assertEquals("FAILED", events.get(events.size() - 1).getStatus());
        assertEquals(
                "TYPED_WORKFLOW_BIND_FAILED",
                events.get(events.size() - 1).getPayload().get("reasonCode"));
    }

    @Test
    void guardedModeMustFailClosedWhenTypedBindingCannotBeEstablished() {
        OpsAgentDefinitionValidator validator = mock(OpsAgentDefinitionValidator.class);
        doThrow(new IllegalStateException("invalid typed definition"))
                .when(validator).compile(org.mockito.ArgumentMatchers.any());
        OpsTypedWorkflowExecutionCoordinator coordinator = coordinator(
                validator, new OpsTypedWorkflowSettings("GUARDED", 3));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> coordinator.begin(
                        definition(), durableRequest(), definition().getNodes(), new ArrayList<>(), null));

        assertEquals("TYPED_WORKFLOW_BIND_FAILED", error.getMessage());
    }

    @Test
    void legacyModeMustBypassTypedCompilationEntirely() {
        OpsAgentDefinitionValidator validator = mock(OpsAgentDefinitionValidator.class);
        doThrow(new IllegalStateException("must not be called"))
                .when(validator).compile(org.mockito.ArgumentMatchers.any());
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsTypedWorkflowExecutionCoordinator coordinator = coordinator(
                validator, new OpsTypedWorkflowSettings("LEGACY", 3));

        assertDoesNotThrow(() -> coordinator.begin(
                definition(), request(), definition().getNodes(), events, null));
        assertEquals(0, events.size());
    }

    @Test
    void ordinaryStateGraphMustSkipTypedDurableLifecycleByDefault() {
        OpsAgentDefinitionValidator validator = mock(OpsAgentDefinitionValidator.class);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsTypedWorkflowExecutionCoordinator coordinator = coordinator(
                validator, new OpsTypedWorkflowSettings("GUARDED", 3));

        assertDoesNotThrow(() -> coordinator.begin(
                definition(), request(), definition().getNodes(), events, null));

        verifyNoInteractions(validator);
        assertEquals("TYPED_WORKFLOW_SKIPPED", events.get(0).getEventType());
        assertEquals("NOT_DURABLE_EXECUTION", events.get(0).getPayload().get("reasonCode"));
    }

    @Test
    void trustedLandingKeepsItsOwnDurableLifecycleInsteadOfCompilingAUserWorkflow() {
        var validator = mock(OpsAgentDefinitionValidator.class);
        var coordinator = coordinator(validator, new OpsTypedWorkflowSettings("GUARDED", 3));
        var definition = new OpsPlatformLandingRuntimeDefinitionFactory().create("project-1");
        var request = durableRequest();
        request.getMetadata().put(OpsAgentRunExecutionContextFactory.TRUSTED_APPROVED_PACKAGE_KEY,
                new cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot(
                        "cp-1", 1, "hash-1", "project-1", "prod", "", java.time.Instant.now().plusSeconds(60)));
        new OpsAgentRunExecutionContextFactory().bindServerContext(request, definition);
        var events = new ArrayList<OpsRuntimeEvent>();
        assertDoesNotThrow(() -> coordinator.begin(definition, request, definition.getNodes(), events, null));
        verifyNoInteractions(validator);
        assertEquals("PLATFORM_LANDING_WORK_SESSION", events.get(0).getPayload().get("reasonCode"));
    }

    @Test
    void platformLabelWithoutServerIssuedLandingAuthorityCannotSkipWorkflowGuards() {
        var validator = mock(OpsAgentDefinitionValidator.class);
        var coordinator = coordinator(validator, new OpsTypedWorkflowSettings("GUARDED", 3));
        var definition = new OpsPlatformLandingRuntimeDefinitionFactory().create("project-1");
        var request = durableRequest();
        request.getMetadata().put("landingApproved", true);
        assertThrows(SecurityException.class,
                () -> coordinator.begin(definition, request, definition.getNodes(), new ArrayList<>(), null));
    }

    private OpsTypedWorkflowExecutionCoordinator coordinator(
            OpsAgentDefinitionValidator validator,
            OpsTypedWorkflowSettings settings) {
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        return new OpsTypedWorkflowExecutionCoordinator(
                validator,
                mock(OpsRuntimeWorkflowBindingAdapter.class),
                mock(OpsDurableWorkflowRuntimeCoordinator.class),
                mock(OpsRuntimeResourceAssembler.class),
                mock(OpsRuntimeContextBundleAdapter.class),
                mock(cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService.class),
                mock(OpsWorkflowApprovalChannelBridge.class),
                settings,
                journal);
    }

    private OpsAgentDefinition definition() {
        return OpsAgentDefinition.builder()
                .agentId("agent-1")
                .name("Agent")
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("node-1")
                        .type("CHAT")
                        .build()))
                .build();
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .projectId("project-1")
                .query("hello")
                .build();
    }

    private OpsAgentChatRequest durableRequest() {
        OpsAgentChatRequest request = request();
        request.getMetadata().put(OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt-1");
        return request;
    }
}
