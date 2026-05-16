package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsWorkSessionFinalizerTest {

    @Test
    void waitingResponseDoesNotWriteAnotherEventAfterDurableLeaseWasReleased() {
        var hooks = mock(OpsWorkSessionFinalizer.Hooks.class);
        var request = new OpsAgentChatRequest(); request.setRunId("scheduled-fixture");
        var waiting = OpsRuntimeEvent.builder().eventType("WORKFLOW_APPROVAL_WAITING")
                .status("WAITING_APPROVAL").nodeId("review-one").build();
        var context = new OpsWorkSessionFinalizer.Context(request,
                OpsAgentDefinition.builder().agentId("agent").version(1).build(), "WORKFLOW", "GRAPH",
                new ArrayList<>(List.of(waiting)), System.nanoTime(), true);
        var result = new OpsWorkSessionFinalizer().waitingApproval(context,"review-one","approval-1",hooks);
        assertThat(result.getMetadata()).containsEntry("status","WAITING_APPROVAL");
        assertThat(result.getEvents()).containsExactly(waiting);
        org.mockito.Mockito.verifyNoInteractions(hooks);
    }

    @Test
    void wrappedSkillRevocationRetainsItsTypedReasonWithoutRevealingDetails() {
        var hooks = mock(OpsWorkSessionFinalizer.Hooks.class);
        var context = new OpsWorkSessionFinalizer.Context(new OpsAgentChatRequest(),
                OpsAgentDefinition.builder().agentId("agent").version(1).build(), "AGENT", "STATE_GRAPH",
                new ArrayList<>(), System.nanoTime(), false);
        var failure = new IllegalStateException("internal graph secret-value", new IllegalStateException(
                "TYPED_WORKFLOW_BIND_FAILED", new cn.lgs.orbisops.application.skill.SkillRuntimeAccessRevokedException()));
        new OpsWorkSessionFinalizer().failed(context, failure, hooks);
        var content = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(hooks).appendAssistant(content.capture(), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("reasonCode", "SKILL_RUNTIME_ACCESS_REVOKED");
        assertThat(content.getValue()).contains("Skill", "后续执行已停止")
                .doesNotContain("secret-value", "未执行任何生产变更");
        verify(hooks).finishDurable(eq("FAILED"), eq("SKILL_RUNTIME_ACCESS_REVOKED"), isNull());
    }

    @Test
    void arbitraryExceptionTextCannotMasqueradeAsSkillRevocation() {
        var hooks = mock(OpsWorkSessionFinalizer.Hooks.class);
        var context = new OpsWorkSessionFinalizer.Context(new OpsAgentChatRequest(),
                OpsAgentDefinition.builder().agentId("agent").version(1).build(), "AGENT", "STATE_GRAPH",
                new ArrayList<>(), System.nanoTime(), false);
        new OpsWorkSessionFinalizer().failed(context, new SecurityException("SKILL_RUNTIME_ACCESS_REVOKED"), hooks);
        verify(hooks).finishDurable(eq("FAILED"), eq("AGENT_RUN_FAILED"), isNull());
    }

    @Test
    void nestedMcpFailuresMustKeepTheirOriginEvenWhenCauseLooksLikeAModelOutage() {
        for (var kind : OpsMcpCallFailure.Kind.values()) {
            var hooks = mock(OpsWorkSessionFinalizer.Hooks.class);
            var request = new OpsAgentChatRequest();
            request.setRunId("mcp-failure-run");
            var context = new OpsWorkSessionFinalizer.Context(request,
                    OpsAgentDefinition.builder().agentId("agent").version(1).build(), "AGENT", "STATE_GRAPH",
                    new ArrayList<>(), System.nanoTime(), false);
            var failure = new IllegalStateException("Graph connection failed", new OpsMcpCallFailure(kind,
                    "REMOTE_CONNECTION_FAILURE", true, new IllegalStateException("401 Unauthorized timeout secret-value")));
            assertThat(OpsModelProviderFailureClassifier.classify(failure)).isEmpty();
            new OpsWorkSessionFinalizer().failed(context, failure, hooks);
            var content = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
            verify(hooks).appendAssistant(content.capture(), metadata.capture());
            assertThat(metadata.getValue()).containsEntry("reasonCode", "MCP_" + kind.name());
            assertThat(content.getValue()).contains("工具", "本轮未完成").doesNotContain("模型", "secret-value", "401", "未执行任何生产变更");
            verify(hooks).finishDurable(eq("FAILED"), eq("MCP_" + kind.name()), isNull());
        }
    }

    @Test
    void providerFailureMustPersistSafeActionableAssistantMessage() {
        OpsWorkSessionFinalizer finalizer = new OpsWorkSessionFinalizer();
        OpsWorkSessionFinalizer.Hooks hooks = mock(OpsWorkSessionFinalizer.Hooks.class);
        OpsAgentChatRequest request = new OpsAgentChatRequest();
        request.setRunId("run-1");
        request.setSessionId("session-1");
        request.setUserId("user-1");
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .version(1)
                .build();
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsWorkSessionFinalizer.Context context = new OpsWorkSessionFinalizer.Context(
                request,
                definition,
                "AGENT",
                "STATE_GRAPH",
                events,
                System.nanoTime(),
                false);

        finalizer.failed(context, new IllegalStateException(
                "Graph execution failed",
                new IllegalStateException(
                        "401 Unauthorized from POST https://provider.invalid/v1/chat/completions")), hooks);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(hooks).appendAssistant(content.capture(), metadata.capture());
        assertThat(content.getValue())
                .contains("鉴权失败")
                .contains("核对已有工具回执与实际资源状态")
                .doesNotContain("401", "https://", "Agent run failed", "未执行任何生产变更");
        assertThat(metadata.getValue()).containsEntry("reasonCode", "MODEL_PROVIDER_AUTH_FAILED");

        ArgumentCaptor<OpsRuntimeEvent> event = ArgumentCaptor.forClass(OpsRuntimeEvent.class);
        verify(hooks).record(event.capture());
        assertThat(event.getValue().getPayload())
                .containsEntry("reasonCode", "MODEL_PROVIDER_AUTH_FAILED");
        assertThat(event.getValue().getSummary()).doesNotContain("401", "https://");
        verify(hooks).finishTaskContext("FAILED", "MODEL_PROVIDER_AUTH_FAILED");
        verify(hooks).finishDurable(eq("FAILED"), eq("MODEL_PROVIDER_AUTH_FAILED"), isNull());
    }
}
