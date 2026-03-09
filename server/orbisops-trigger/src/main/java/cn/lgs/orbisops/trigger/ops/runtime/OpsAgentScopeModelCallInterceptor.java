package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Adds durable model-call telemetry and a hard deadline to AgentScope/ReAct model calls. */
final class OpsAgentScopeModelCallInterceptor extends ModelInterceptor {

    private final ExecutorService modelCallExecutor;
    private final int timeoutSeconds;
    private final List<OpsRuntimeEvent> events;
    private final Consumer<OpsRuntimeEvent> eventSink;
    private final String agentName;

    OpsAgentScopeModelCallInterceptor(
            ExecutorService modelCallExecutor,
            int timeoutSeconds,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String agentName) {
        this.modelCallExecutor = modelCallExecutor;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.events = events;
        this.eventSink = eventSink;
        this.agentName = agentName == null ? "" : agentName;
    }

    @Override
    public String getName() {
        return "ops-agent-scope-model-call";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        if (modelCallExecutor == null) {
            long startedNanos = System.nanoTime();
            record(event("MODEL_CALL_STARTED", "RUNNING", "AgentScope 模型调用已开始。", request, 0L, 0L, null));
            try {
                ModelResponse response = handler.call(request);
                record(event("MODEL_CALL_FINISHED", "SUCCEEDED", "AgentScope 模型调用完成。",
                        request, elapsedMs(startedNanos), 0L, null));
                return response;
            } catch (RuntimeException error) {
                record(event("MODEL_CALL_FAILED", "FAILED", "AgentScope 模型调用失败：" + error.getMessage(),
                        request, elapsedMs(startedNanos), 0L, error.getMessage()));
                throw error;
            }
        }
        return boundedCall(request, handler);
    }

    private ModelResponse boundedCall(ModelRequest request, ModelCallHandler handler) {
        long queuedNanos = System.nanoTime();
        CountDownLatch started = new CountDownLatch(1);
        AtomicLong startedNanos = new AtomicLong();
        record(event("MODEL_CALL_QUEUED", "RUNNING", "AgentScope 模型调用等待执行槽。", request, 0L, 0L, null));
        Future<ModelResponse> future = modelCallExecutor.submit(() -> {
            startedNanos.set(System.nanoTime());
            started.countDown();
            long queueWaitMs = elapsedMs(queuedNanos);
            record(event("MODEL_CALL_STARTED", "RUNNING", "AgentScope 模型调用已开始。", request, 0L, queueWaitMs, null));
            return handler.call(request);
        });
        long timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);
        long queueWaitBudgetMillis = OpsNodeDeadlineContext.remainingMillis(timeoutMillis * 2L);
        try {
            if (!started.await(queueWaitBudgetMillis, TimeUnit.MILLISECONDS)) {
                future.cancel(true);
                IllegalStateException error = new IllegalStateException(
                        "MODEL_CALL_QUEUE_TIMEOUT: exceeded " + queueWaitBudgetMillis + "ms");
                record(event("MODEL_CALL_FAILED", "FAILED", "AgentScope 模型调用等待执行槽超时。",
                        request, 0L, elapsedMs(queuedNanos), error.getMessage()));
                throw error;
            }
            long executionTimeoutMillis = OpsNodeDeadlineContext.remainingMillis(timeoutMillis);
            if (executionTimeoutMillis <= 0L) {
                future.cancel(true);
                IllegalStateException error = new IllegalStateException("MODEL_CALL_NODE_DEADLINE_EXHAUSTED");
                record(event("MODEL_CALL_FAILED", "FAILED", "AgentScope 模型调用开始前节点总时限已耗尽。",
                        request, 0L, elapsedMs(queuedNanos), error.getMessage()));
                throw error;
            }
            ModelResponse response = future.get(executionTimeoutMillis, TimeUnit.MILLISECONDS);
            long executionMs = elapsedMs(startedNanos.get());
            long queueWaitMs = nanosToMillis(startedNanos.get() - queuedNanos);
            record(event("MODEL_CALL_FINISHED", "SUCCEEDED", "AgentScope 模型调用完成。",
                    request, executionMs, queueWaitMs, null));
            return response;
        } catch (TimeoutException error) {
            future.cancel(true);
            IllegalStateException wrapped = new IllegalStateException(
                    "MODEL_CALL_TIMEOUT: exceeded " + timeoutSeconds + "s", error);
            record(event("MODEL_CALL_FAILED", "FAILED", "AgentScope 模型调用失败：" + wrapped.getMessage(),
                    request, elapsedMs(startedNanos.get()), nanosToMillis(startedNanos.get() - queuedNanos), wrapped.getMessage()));
            throw wrapped;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            IllegalStateException wrapped = new IllegalStateException("MODEL_CALL_INTERRUPTED", error);
            record(event("MODEL_CALL_FAILED", "FAILED", "AgentScope 模型调用失败：MODEL_CALL_INTERRUPTED",
                    request, startedNanos.get() == 0L ? 0L : elapsedMs(startedNanos.get()), elapsedMs(queuedNanos), wrapped.getMessage()));
            throw wrapped;
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            RuntimeException runtimeException = cause instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("MODEL_CALL_FAILED: " + cause.getMessage(), cause);
            record(event("MODEL_CALL_FAILED", "FAILED", "AgentScope 模型调用失败：" + runtimeException.getMessage(),
                    request, elapsedMs(startedNanos.get()), nanosToMillis(startedNanos.get() - queuedNanos), runtimeException.getMessage()));
            throw runtimeException;
        }
    }

    private OpsRuntimeEvent event(
            String eventType,
            String status,
            String summary,
            ModelRequest request,
            long durationMs,
            long queueWaitMs,
            String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("owner", "AGENTSCOPE");
        payload.put("agent", agentName);
        payload.put("messageCount", request == null || request.getMessages() == null ? 0 : request.getMessages().size());
        payload.put("toolNames", request == null || request.getTools() == null ? List.of() : List.copyOf(request.getTools()));
        payload.put("dynamicToolNames", request == null || request.getDynamicToolCallbacks() == null
                ? List.of()
                : request.getDynamicToolCallbacks().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(callback -> callback.getToolDefinition() == null ? "" : callback.getToolDefinition().name())
                        .filter(name -> name != null && !name.isBlank())
                        .toList());
        payload.put("timeoutSeconds", timeoutSeconds);
        if (queueWaitMs > 0) {
            payload.put("queueWaitMs", queueWaitMs);
        }
        if (durationMs > 0) {
            payload.put("durationMs", durationMs);
        }
        if (error != null && !error.isBlank()) {
            payload.put("error", error);
        }
        return OpsRuntimeEvent.builder()
                .eventType(eventType)
                .agent(agentName)
                .status(status)
                .summary(summary)
                .payload(payload)
                .build();
    }

    private void record(OpsRuntimeEvent event) {
        if (events != null) {
            events.add(event);
        }
        if (eventSink != null) {
            eventSink.accept(event);
        }
    }

    private long elapsedMs(long startedNanos) {
        return nanosToMillis(System.nanoTime() - startedNanos);
    }

    private long nanosToMillis(long nanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos)));
    }
}
