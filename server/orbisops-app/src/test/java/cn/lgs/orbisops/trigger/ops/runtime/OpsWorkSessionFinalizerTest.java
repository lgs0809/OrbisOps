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
                .contains("未执行任何生产变更")
                .doesNotContain("401", "https://", "Agent run failed");
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
