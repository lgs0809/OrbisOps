package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpsAgentScopeModelCallInterceptorTest {

    @Test
    void shouldRecordDurableStartAndFinishAroundAgentScopeModelCall() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<OpsRuntimeEvent> events = new ArrayList<>();
            OpsAgentScopeModelCallInterceptor interceptor = new OpsAgentScopeModelCallInterceptor(
                    executor, 5, events, ignored -> { }, "agent-a");
            ModelRequest request = ModelRequest.builder()
                    .messages(List.of(new UserMessage("check metrics")))
                    .build();

            ModelResponse response = interceptor.interceptModel(
                    request,
                    ignored -> ModelResponse.of(new AssistantMessage("ok")));

            assertThat(response).isNotNull();
            assertThat(events).extracting(OpsRuntimeEvent::getEventType)
                    .containsExactly("MODEL_CALL_QUEUED", "MODEL_CALL_STARTED", "MODEL_CALL_FINISHED");
            assertThat(events.get(2).getPayload())
                    .containsEntry("owner", "AGENTSCOPE")
                    .containsEntry("agent", "agent-a")
                    .containsEntry("timeoutSeconds", 5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void executorQueueWaitMustNotConsumeAgentScopeModelCallDeadline() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    Thread.sleep(1_100L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread.sleep(20L);
            List<OpsRuntimeEvent> events = new ArrayList<>();
            OpsAgentScopeModelCallInterceptor interceptor = new OpsAgentScopeModelCallInterceptor(
                    executor, 1, events, ignored -> { }, "agent-queued");
            ModelRequest request = ModelRequest.builder()
                    .messages(List.of(new UserMessage("queued")))
                    .build();

            ModelResponse response = interceptor.interceptModel(
                    request,
                    ignored -> ModelResponse.of(new AssistantMessage("ok-after-queue")));

            assertThat(response).isNotNull();
            assertThat(events).extracting(OpsRuntimeEvent::getEventType)
                    .containsExactly("MODEL_CALL_QUEUED", "MODEL_CALL_STARTED", "MODEL_CALL_FINISHED");
            assertThat(events.get(1).getPayload()).containsKey("queueWaitMs");
            assertThat((Long) events.get(1).getPayload().get("queueWaitMs")).isGreaterThanOrEqualTo(900L);
            assertThat(events.get(2).getPayload().getOrDefault("durationMs", 0L)).isInstanceOf(Long.class);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCancelAndRecordFailedWhenAgentScopeModelCallExceedsHardDeadline() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<OpsRuntimeEvent> events = new ArrayList<>();
            OpsAgentScopeModelCallInterceptor interceptor = new OpsAgentScopeModelCallInterceptor(
                    executor, 1, events, ignored -> { }, "agent-timeout");
            ModelRequest request = ModelRequest.builder()
                    .messages(List.of(new UserMessage("slow")))
                    .build();

            assertThatThrownBy(() -> interceptor.interceptModel(request, ignored -> {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
                return ModelResponse.of(new AssistantMessage("late"));
            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MODEL_CALL_TIMEOUT")
                    .hasMessageContaining("1s");

            assertThat(events).extracting(OpsRuntimeEvent::getEventType)
                    .containsExactly("MODEL_CALL_QUEUED", "MODEL_CALL_STARTED", "MODEL_CALL_FAILED");
            assertThat(events.get(2).getPayload())
                    .containsEntry("timeoutSeconds", 1)
                    .containsKey("durationMs")
                    .containsKey("error");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void streamingModelCallMustFinishOnlyAfterTheFluxCompletes() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<OpsRuntimeEvent> events = new ArrayList<>();
            OpsAgentScopeModelCallInterceptor interceptor = new OpsAgentScopeModelCallInterceptor(
                    executor, 5, events, ignored -> { }, "agent-stream");
            ModelRequest request = ModelRequest.builder()
                    .messages(List.of(new UserMessage("stream")))
                    .build();

            ModelResponse response = interceptor.interceptModel(
                    request,
                    ignored -> ModelResponse.of(Flux.just(new ChatResponse(List.of(
                            new Generation(new AssistantMessage("ok")))))));

            assertThat(events).extracting(OpsRuntimeEvent::getEventType)
                    .containsExactly("MODEL_CALL_QUEUED", "MODEL_CALL_STARTED");
            ((Flux<?>) response.getMessage()).blockLast();
            assertThat(events).extracting(OpsRuntimeEvent::getEventType)
                    .containsExactly("MODEL_CALL_QUEUED", "MODEL_CALL_STARTED", "MODEL_CALL_FINISHED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void streamingProviderAuthFailureMustNeverBeRecordedAsSuccess() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<OpsRuntimeEvent> events = new ArrayList<>();
            OpsAgentScopeModelCallInterceptor interceptor = new OpsAgentScopeModelCallInterceptor(
                    executor, 5, events, ignored -> { }, "agent-stream-auth");
            ModelRequest request = ModelRequest.builder()
                    .messages(List.of(new UserMessage("stream")))
                    .build();
            ModelResponse response = interceptor.interceptModel(
                    request,
                    ignored -> ModelResponse.of(Flux.error(new IllegalStateException(
                            "401 Unauthorized from POST https://provider.invalid/v1/chat/completions"))));

            assertThatThrownBy(() -> ((Flux<?>) response.getMessage()).blockLast())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("MODEL_PROVIDER_AUTH_FAILED");
            assertThat(events).extracting(OpsRuntimeEvent::getEventType)
                    .containsExactly("MODEL_CALL_QUEUED", "MODEL_CALL_STARTED", "MODEL_CALL_FAILED")
                    .doesNotContain("MODEL_CALL_FINISHED");
            assertThat(events.get(2).getSummary()).contains("MODEL_PROVIDER_AUTH_FAILED").doesNotContain("https://");
            assertThat(events.get(2).getPayload())
                    .containsEntry("error", "MODEL_PROVIDER_AUTH_FAILED");
        } finally {
            executor.shutdownNow();
        }
    }
}
