package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Executes runtime model calls and emits the model-call telemetry contract. */
final class OpsRuntimeLlmInvoker {

    private static final int DEFAULT_MODEL_CALL_TIMEOUT_SECONDS = 240;

    private final ModelAvailabilityPort aiModelAvailability;
    private final ExecutorService modelCallExecutor;
    private final int modelCallTimeoutSeconds;

    OpsRuntimeLlmInvoker(ModelAvailabilityPort aiModelAvailability) {
        this(aiModelAvailability, null, DEFAULT_MODEL_CALL_TIMEOUT_SECONDS);
    }

    OpsRuntimeLlmInvoker(
            ModelAvailabilityPort aiModelAvailability,
            ExecutorService modelCallExecutor,
            int modelCallTimeoutSeconds) {
        this.aiModelAvailability = aiModelAvailability;
        this.modelCallExecutor = modelCallExecutor;
        this.modelCallTimeoutSeconds = Math.max(1, modelCallTimeoutSeconds);
    }

    String call(String systemPrompt,
                String userPrompt,
                OpsRuntimeResourceBundle bundle,
                List<OpsRuntimeEvent> events,
                Consumer<OpsRuntimeEvent> eventSink,
                long requestStartedNanos) {
        assertAvailable(events, eventSink, requestStartedNanos);
        ChatClient.Builder builder = clientBuilder(systemPrompt, bundle);
        long startedNanos = System.nanoTime();
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("MODEL_CALL_STARTED")
                .status("RUNNING")
                .summary("模型调用已发起。")
                .payload(payloadWithElapsed(modelCallPayload(systemPrompt, userPrompt, bundle), requestStartedNanos))
                .build());
        String content;
        try {
            content = boundedModelCall(() -> builder.build()
                    .prompt()
                    .user(userPrompt)
                    .call()
                    .content());
        } catch (RuntimeException error) {
            Map<String, Object> failedPayload = modelCallPayload(systemPrompt, userPrompt, bundle);
            failedPayload.put("durationMs", elapsedMs(startedNanos));
            failedPayload.put("error", error.getMessage());
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("MODEL_CALL_FAILED")
                    .status("FAILED")
                    .summary("模型调用失败：" + error.getMessage())
                    .payload(payloadWithElapsed(failedPayload, requestStartedNanos))
                    .build());
            throw error;
        }
        Map<String, Object> finishedPayload = modelCallPayload(systemPrompt, userPrompt, bundle);
        finishedPayload.put("durationMs", elapsedMs(startedNanos));
        finishedPayload.put("outputChars", content == null ? 0 : content.length());
        finishedPayload.put("output", OpsMemoryTextUtils.abbreviate(content, 4000));
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("MODEL_CALL_FINISHED")
                .status("SUCCEEDED")
                .summary("模型调用完成。")
                .payload(payloadWithElapsed(finishedPayload, requestStartedNanos))
                .build());
        return content == null ? "" : content;
    }

    String callStreaming(String systemPrompt,
                         String userPrompt,
                         OpsRuntimeResourceBundle bundle,
                         List<OpsRuntimeEvent> events,
                         Consumer<OpsRuntimeEvent> eventSink,
                         long requestStartedNanos) {
        ChatClient.Builder builder = clientBuilder(systemPrompt, bundle);
        StringBuilder content = new StringBuilder();
        long startedNanos = System.nanoTime();
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("MODEL_CALL_STARTED")
                .status("RUNNING")
                .summary("主模型流式调用已发起，等待首个输出片段。")
                .payload(payloadWithElapsed(modelCallPayload(systemPrompt, userPrompt, bundle), requestStartedNanos))
                .build());
        AtomicBoolean firstChunk = new AtomicBoolean(false);
        try {
            boundedModelCall(() -> builder.build()
                    .prompt()
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        if (chunk == null || chunk.isEmpty()) return;
                        if (firstChunk.compareAndSet(false, true)) {
                            long ttftMs = elapsedMs(startedNanos);
                            record(events, eventSink, OpsRuntimeEvent.builder()
                                    .eventType("MODEL_TTFT")
                                    .status("SUCCEEDED")
                                    .summary("模型首个输出片段已返回。")
                                    .payload(payloadWithElapsed(Map.of(
                                            "modelTtftMs", ttftMs,
                                            "requestToFirstTokenMs", elapsedMs(requestStartedNanos)), requestStartedNanos))
                                    .build());
                        }
                        content.append(chunk);
                        record(events, eventSink, OpsRuntimeEvent.builder()
                                .eventType("TEXT_DELTA")
                                .status("RUNNING")
                                .summary("模型输出片段。")
                                .content(chunk)
                                .build());
                    })
                    .blockLast());
        } catch (RuntimeException error) {
            Map<String, Object> failedPayload = modelCallPayload(systemPrompt, userPrompt, bundle);
            failedPayload.put("durationMs", elapsedMs(startedNanos));
            failedPayload.put("outputChars", content.length());
            failedPayload.put("error", error.getMessage());
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("MODEL_CALL_FAILED")
                    .status("FAILED")
                    .summary("模型流式调用失败：" + error.getMessage())
                    .payload(payloadWithElapsed(failedPayload, requestStartedNanos))
                    .build());
            throw error;
        }
        Map<String, Object> finishedPayload = modelCallPayload(systemPrompt, userPrompt, bundle);
        finishedPayload.put("durationMs", elapsedMs(startedNanos));
        finishedPayload.put("outputChars", content.length());
        finishedPayload.put("output", OpsMemoryTextUtils.abbreviate(content.toString(), 4000));
        finishedPayload.put("streaming", true);
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("MODEL_CALL_FINISHED")
                .status("SUCCEEDED")
                .summary("模型流式调用完成。")
                .payload(payloadWithElapsed(finishedPayload, requestStartedNanos))
                .build());
        return content.toString();
    }

    Map<String, Object> modelCallPayload(String systemPrompt,
                                         String userPrompt,
                                         OpsRuntimeResourceBundle bundle) {
        Map<String, Object> payload = runtimeResourcePayload(bundle);
        payload.put("systemPromptChars", systemPrompt == null ? 0 : systemPrompt.length());
        payload.put("userPromptChars", userPrompt == null ? 0 : userPrompt.length());
        payload.put("promptChars", (systemPrompt == null ? 0 : systemPrompt.length())
                + (userPrompt == null ? 0 : userPrompt.length()));
        payload.put("systemPrompt", OpsMemoryTextUtils.abbreviate(systemPrompt, 4000));
        payload.put("userPrompt", OpsMemoryTextUtils.abbreviate(userPrompt, 4000));
        return payload;
    }

    Map<String, Object> runtimeResourcePayload(OpsRuntimeResourceBundle bundle) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (bundle == null) return payload;
        if (bundle.getMetadata() != null) payload.putAll(bundle.getMetadata());
        payload.put("modelId", text(bundle.getModelId()));
        payload.put("projectId", text(bundle.getProjectId()));
        payload.put("agentVersion", bundle.getAgentVersion());
        payload.put("toolCount", bundle.getTools() == null ? 0 : bundle.getTools().size());
        payload.put("skillCount", bundle.getSkillNames() == null ? 0 : bundle.getSkillNames().size());
        payload.put("skillNames", bundle.getSkillNames() == null ? List.of() : new ArrayList<>(bundle.getSkillNames()));
        payload.put("mcpIds", bundle.getMcpIds() == null ? List.of() : new ArrayList<>(bundle.getMcpIds()));
        payload.put("mcpServerCount", bundle.getMcpServers() == null ? 0 : bundle.getMcpServers().size());
        payload.put("ragEnabled", Boolean.TRUE.equals(bundle.getRagEnabled()));
        payload.put("knowledgeBaseId", text(bundle.getKnowledgeBaseId()));
        return payload;
    }

    private void assertAvailable(List<OpsRuntimeEvent> events,
                                 Consumer<OpsRuntimeEvent> eventSink,
                                 long requestStartedNanos) {
        if (aiModelAvailability != null && aiModelAvailability.isChatAvailable()) return;
        String message = aiModelAvailability == null
                ? "Agent 模型调用被阻断：模型可用性服务未初始化，禁止启动模型调用。"
                : aiModelAvailability.unavailableMessage("Agent 模型调用");
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("MODEL_CALL_BLOCKED")
                .status("BLOCKED")
                .summary(message)
                .payload(payloadWithElapsed(Map.of(
                        "reasonCode", "MODEL_UNAVAILABLE",
                        "modelAvailable", false), requestStartedNanos))
                .build());
        throw new IllegalStateException(message);
    }

    private ChatClient.Builder clientBuilder(String systemPrompt, OpsRuntimeResourceBundle bundle) {
        ChatClient.Builder builder = ChatClient.builder(bundle.getChatModel()).defaultSystem(systemPrompt);
        if (bundle.getTools() != null && !bundle.getTools().isEmpty()) {
            builder.defaultToolCallbacks(bundle.getTools());
        }
        return builder;
    }

    <T> T boundedModelCall(Supplier<T> invocation) {
        if (modelCallExecutor == null) {
            return invocation.get();
        }
        long timeoutMillis = OpsNodeDeadlineContext.remainingMillis(
                TimeUnit.SECONDS.toMillis(modelCallTimeoutSeconds));
        if (timeoutMillis <= 0L) {
            throw new IllegalStateException("模型调用开始前节点总时限已耗尽");
        }
        Long nodeDeadline = OpsNodeDeadlineContext.captureDeadline();
        Future<T> future = modelCallExecutor.submit(
                () -> OpsNodeDeadlineContext.withDeadline(nodeDeadline, invocation));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("模型调用超过当前总时限 " + timeoutMillis + "ms", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模型调用被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("模型调用失败", cause);
        }
    }

    private void record(List<OpsRuntimeEvent> events,
                        Consumer<OpsRuntimeEvent> eventSink,
                        OpsRuntimeEvent event) {
        events.add(event);
        if (eventSink != null) eventSink.accept(event);
    }

    private Map<String, Object> payloadWithElapsed(Map<String, Object> seed, long requestStartedNanos) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (seed != null) payload.putAll(seed);
        payload.put("elapsedMs", elapsedMs(requestStartedNanos));
        return payload;
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
