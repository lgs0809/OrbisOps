package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStyle;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeToolAuthorityPolicyTest {

    private final OpsRuntimeToolAuthorityPolicy policy = new OpsRuntimeToolAuthorityPolicy();

    @Test
    void observeOnlyAllowsReadAndDelegatedDiscoveryButNotWrite() {
        OpsRuntimeResourceContext context = context(
                authority(AgentExecutionStage.INVESTIGATE, Instant.now().plusSeconds(60)),
                "OBSERVE_ONLY",
                governedReadOnly("search_logs"),
                governedDelegated("search_tools"),
                governedWrite("update_prod", Set.of(AgentExecutionStage.LANDING)));

        policy.enforce(context);

        assertEquals(List.of("search_logs", "search_tools"), names(context));
        assertEquals("OBSERVE_ONLY", context.getMetadata().get("agentAuthority"));
    }

    @Test
    void prepareChangeIsOrthogonalToTriggerStageAndTurnsProdWriteIntoProposal() {
        ToolCallback repair = governedRepair("code_edit");
        ToolCallback testWrite = governedWrite(
                "update_test", Set.of(AgentExecutionStage.PREPARE, AgentExecutionStage.LANDING));
        ToolCallback prodWrite = governedWrite("update_prod", Set.of(AgentExecutionStage.LANDING));
        OpsRuntimeResourceContext context = context(
                authority(AgentExecutionStage.INVESTIGATE, Instant.now().plusSeconds(60)),
                "PREPARE_CHANGE",
                repair, testWrite, prodWrite);
        when(unwrap(repair).call(anyString())).thenReturn("patched");
        when(unwrap(testWrite).call(anyString())).thenReturn("validated");

        policy.enforce(context);

        assertEquals(List.of("code_edit", "update_test", "update_prod"), names(context));
        assertEquals("patched", context.getTools().get(0).call("{}"));
        assertEquals("validated", context.getTools().get(1).call("{}"));
        String proposal = context.getTools().get(2).call("{\"replicas\":3}");
        assertTrue(proposal.contains("REQUIRES_CHANGE_PACKAGE"));
        assertTrue(proposal.contains("PROPOSABLE_ONLY"));
        assertTrue(proposal.contains("\"remoteCallExecuted\":false"));
        verify(unwrap(prodWrite), never()).call(anyString());
        assertTrue(context.getEvents().stream()
                .anyMatch(event -> "PROPOSED_ACTION_RECORDED".equals(event.getEventType())));
    }

    @Test
    void observeOnlyCannotUsePrepareCapabilitiesEvenWhenRunStageIsPrepare() {
        OpsRuntimeResourceContext context = context(
                authority(AgentExecutionStage.PREPARE, Instant.now().plusSeconds(60)),
                "OBSERVE_ONLY",
                governedReadOnly("search_logs"),
                governedRepair("code_edit"),
                governedWrite("update_test", Set.of(AgentExecutionStage.PREPARE, AgentExecutionStage.LANDING)));

        policy.enforce(context);

        assertEquals(List.of("search_logs"), names(context));
    }

    @Test
    void trustedChannelObserveOnlyDowngradeOverridesPrepareAuthorityAndHidesMutationTools() {
        ToolCallback repair = governedRepair("code_edit");
        ToolCallback write = governedWrite(
                "update_test", Set.of(AgentExecutionStage.PREPARE, AgentExecutionStage.LANDING));
        OpsRuntimeResourceContext context = context(
                authority(AgentExecutionStage.PREPARE, Instant.now().plusSeconds(60)),
                "PREPARE_CHANGE",
                governedReadOnly("search_logs"), repair, write);
        context.getRequest().setTrustedObserveOnly(true);

        policy.enforce(context);

        assertEquals(List.of("search_logs"), names(context));
        assertEquals("OBSERVE_ONLY", context.getMetadata().get("agentAuthority"));
        verify(unwrap(repair), never()).call(anyString());
        verify(unwrap(write), never()).call(anyString());
    }

    @Test
    void untrustedContextCannotElevateItselfWithPrepareChangeConfiguration() {
        ToolCallback write = governedWrite(
                "update_test", Set.of(AgentExecutionStage.PREPARE, AgentExecutionStage.LANDING));
        OpsRuntimeResourceContext context = context(null, "PREPARE_CHANGE", governedReadOnly("search_logs"), write);

        policy.enforce(context);

        assertEquals(List.of("search_logs"), names(context));
        assertEquals("OBSERVE_ONLY", context.getMetadata().get("agentAuthority"));
        verify(unwrap(write), never()).call(anyString());
    }

    @Test
    void landingExposesProjectReadWriteAndProgressiveDispatcherAfterApproval() {
        ToolCallback read = governedReadOnly("get_deployment");
        ToolCallback write = governedWrite("update_deployment", Set.of(AgentExecutionStage.LANDING));
        ToolCallback delegated = governedDelegated("project_mcp_ops");
        OpsRuntimeResourceContext context = context(
                authority(AgentExecutionStage.LANDING, Instant.now().plusSeconds(60)),
                "OBSERVE_ONLY",
                read, write, delegated);
        when(unwrap(write).call(anyString())).thenReturn("updated");

        policy.enforce(context);

        assertEquals(List.of("get_deployment", "update_deployment", "project_mcp_ops"), names(context));
        assertEquals("updated", context.getTools().get(1).call("{\"replicas\":3}"));
        assertEquals("PROD_FULL", context.getMetadata().get("agentAuthority"));
    }

    @Test
    void landingApprovalExpiryRevokesAllToolExecution() {
        OpsRuntimeResourceContext context = context(
                authority(AgentExecutionStage.LANDING, Instant.now().minusSeconds(1)),
                "OBSERVE_ONLY",
                governedWrite("update_deployment", Set.of(AgentExecutionStage.LANDING)));

        assertThrows(SecurityException.class, () -> policy.enforce(context));
    }

    @Test
    void userConfigurationCannotRequestProdFull() {
        OpsRuntimeResourceContext context = context(
                authority(AgentExecutionStage.PREPARE, Instant.now().plusSeconds(60)),
                "PROD_FULL",
                governedReadOnly("search_logs"));

        SecurityException error = assertThrows(SecurityException.class, () -> policy.enforce(context));
        assertTrue(error.getMessage().contains("AGENT_PROD_FULL_AUTHORITY_FORBIDDEN"));
    }

    @Test
    void ungovernedCallbackFailsClosed() {
        ToolCallback raw = raw("search_logs");
        OpsRuntimeResourceContext context = context(
                authority(AgentExecutionStage.INVESTIGATE, Instant.now().plusSeconds(60)),
                "OBSERVE_ONLY", raw);

        policy.enforce(context);

        assertEquals(List.of(), names(context));
        verify(raw, never()).call(anyString());
        assertFalse(context.getEvents().isEmpty());
    }

    private OpsRuntimeResourceContext context(
            AgentRunExecutionContext authority,
            String nodeAuthority,
            ToolCallback... tools) {
        return OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .node(OpsWorkflowNode.builder()
                        .nodeId("node-1")
                        .type("AGENT")
                        .config(Map.of("authority", nodeAuthority))
                        .build())
                .executionContext(authority)
                .request(OpsAgentChatRequest.builder().runId("run-1").build())
                .tools(new ArrayList<>(List.of(tools)))
                .events(new ArrayList<>())
                .build();
    }

    private AgentRunExecutionContext authority(AgentExecutionStage stage, Instant deadline) {
        AgentSnapshot agent = new AgentSnapshot(
                "agent-1", 1, "definition-hash", "prompt-hash", "model-1",
                Set.of(), Set.of("mcp-1"), Set.of(), Set.of());
        Optional<ApprovedPackageSnapshot> approved = stage == AgentExecutionStage.LANDING
                ? Optional.of(new ApprovedPackageSnapshot(
                        "pkg-1", 1, "package-hash", "project-1", "prod", "artifact-hash", deadline))
                : Optional.empty();
        return new AgentRunExecutionContext(
                "run-1", "session-1", "project-1",
                stage == AgentExecutionStage.LANDING ? TriggerSource.LANDING : TriggerSource.CHAT,
                AgentExecutionStyle.REACT,
                stage,
                agent,
                approved,
                stage == AgentExecutionStage.LANDING
                        ? CapabilityProfile.PROD_FULL
                        : stage == AgentExecutionStage.PREPARE
                        ? CapabilityProfile.TEST_FULL
                        : CapabilityProfile.PROD_DIAGNOSTIC,
                Set.of("project-resource"),
                Set.of(),
                deadline);
    }

    private ToolCallback governedReadOnly(String name) {
        return OpsRuntimeGovernedToolCallback.wrap(
                raw(name),
                OpsRuntimeToolAuthorityDescriptor.readOnly(
                        "TEST_READ",
                        Set.of(AgentExecutionStage.INVESTIGATE, AgentExecutionStage.PREPARE, AgentExecutionStage.LANDING),
                        Set.of(name)));
    }

    private ToolCallback governedRepair(String name) {
        return OpsRuntimeGovernedToolCallback.wrap(
                raw(name),
                OpsRuntimeToolAuthorityDescriptor.repairWorkspace(
                        "TEST_REPAIR", Set.of(AgentExecutionStage.PREPARE), Set.of(name)));
    }

    private ToolCallback governedWrite(String name, Set<AgentExecutionStage> stages) {
        return OpsRuntimeGovernedToolCallback.wrap(
                raw(name),
                OpsRuntimeToolAuthorityDescriptor.targetWrite("TEST_WRITE", stages, Set.of(name)));
    }

    private ToolCallback governedDelegated(String name) {
        return OpsRuntimeGovernedToolCallback.wrap(
                raw(name),
                OpsRuntimeToolAuthorityDescriptor.delegated(
                        "TEST_DELEGATED",
                        Set.of(AgentExecutionStage.INVESTIGATE, AgentExecutionStage.PREPARE, AgentExecutionStage.LANDING),
                        Set.of(name)));
    }

    private ToolCallback raw(String name) {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        when(definition.description()).thenReturn(name);
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }

    private ToolCallback unwrap(ToolCallback callback) {
        return callback instanceof OpsRuntimeGovernedToolCallback governed ? governed.delegate() : callback;
    }

    private List<String> names(OpsRuntimeResourceContext context) {
        return context.getTools().stream()
                .map(ToolCallback::getToolDefinition)
                .map(ToolDefinition::name)
                .toList();
    }
}
