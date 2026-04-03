package cn.lgs.orbisops.trigger.ops;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Executes one Spring AI model call with trace propagation, total deadline and cancellation. */
public final class OpsLlmModelCallExecutor {

    private final ExecutorService executorService;

    public OpsLlmModelCallExecutor(ExecutorService executorService) {
        this.executorService = executorService;
    }

    ExecutionResult<String> execute(Input input) {
        SubmittedCall<String> submitted = submit(
                () -> invoke(input),
                input.trace());
        long timeoutMillis = TimeUnit.SECONDS.toMillis(Math.max(1, input.timeoutSeconds()));
        return await(submitted, timeoutMillis);
    }

    public <T> T executeCall(
            Callable<T> callable,
            OpsLlmTraceContext.Trace trace,
            long timeoutMillis) {
        return await(submit(callable, trace), timeoutMillis).value();
    }

    private <T> SubmittedCall<T> submit(
            Callable<T> callable,
            OpsLlmTraceContext.Trace trace) {
        CountDownLatch started = new CountDownLatch(1);
        AtomicLong startedNanos = new AtomicLong();
        long queuedNanos = System.nanoTime();
        Long nodeDeadline = OpsNodeDeadlineContext.captureDeadline();
        Future<T> future = executorService.submit(() -> {
            startedNanos.set(System.nanoTime());
            started.countDown();
            return OpsNodeDeadlineContext.withDeadline(nodeDeadline,
                    () -> OpsLlmTraceContext.withTrace(trace, () -> call(callable)));
        });
        return new SubmittedCall<>(future, started, queuedNanos, startedNanos);
    }

    private <T> ExecutionResult<T> await(SubmittedCall<T> submitted, long timeoutMillis) {
        if (timeoutMillis <= 0L) {
            submitted.future().cancel(true);
            throw new IllegalStateException("模型调用开始前节点总时限已耗尽");
        }
        long queueWaitBudgetMillis = OpsNodeDeadlineContext.remainingMillis(doubled(timeoutMillis));
        long executionTimeoutMillis = timeoutMillis;
        try {
            if (!submitted.started().await(queueWaitBudgetMillis, TimeUnit.MILLISECONDS)) {
                submitted.future().cancel(true);
                throw new IllegalStateException(
                        "模型调用等待执行槽超过当前总时限 " + queueWaitBudgetMillis + "ms");
            }
            long queueWaitMillis = nanosToMillis(
                    submitted.startedNanos().get() - submitted.queuedNanos());
            executionTimeoutMillis = OpsNodeDeadlineContext.remainingMillis(timeoutMillis);
            if (executionTimeoutMillis <= 0L) {
                submitted.future().cancel(true);
                throw new IllegalStateException("模型调用开始前节点总时限已耗尽");
            }
            T value = submitted.future().get(executionTimeoutMillis, TimeUnit.MILLISECONDS);
            long executionMillis = nanosToMillis(
                    System.nanoTime() - submitted.startedNanos().get());
            return new ExecutionResult<>(value, queueWaitMillis, executionMillis);
        } catch (TimeoutException e) {
            submitted.future().cancel(true);
            throw new IllegalStateException(
                    "模型调用超过当前执行/节点总时限 " + executionTimeoutMillis + "ms",
                    e);
        } catch (InterruptedException e) {
            submitted.future().cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模型调用被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("模型调用失败", cause);
        }
    }

    private long doubled(long value) {
        if (value >= Long.MAX_VALUE / 2L) return Long.MAX_VALUE;
        return Math.max(1L, value * 2L);
    }

    private long nanosToMillis(long nanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos)));
    }

    private String invoke(Input input) {
        ChatClient.Builder builder = ChatClient.builder(input.chatModel())
                .defaultSystem(input.systemPrompt());
        if (input.toolCallback() != null) {
            builder.defaultToolCallbacks(input.toolCallback());
        }
        ChatClient.ChatClientRequestSpec requestSpec = builder.build().prompt();
        if (input.options() != null) {
            requestSpec = requestSpec.options(input.options());
        }
        return requestSpec.user(input.userPrompt()).call().content();
    }

    private <T> T call(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    record Input(
            ChatModel chatModel,
            String systemPrompt,
            String userPrompt,
            ToolCallback toolCallback,
            OpenAiChatOptions options,
            OpsLlmTraceContext.Trace trace,
            int timeoutSeconds) {
    }

    record ExecutionResult<T>(T value, long queueWaitMs, long executionMs) {
    }

    private record SubmittedCall<T>(
            Future<T> future,
            CountDownLatch started,
            long queuedNanos,
            AtomicLong startedNanos) {
    }
}
