package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Coordinates synchronous Work Session timeout, failure unwrapping, and durable resume signaling. */
final class OpsChatSyncExecutionCoordinator {

    private final ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession;
    private final GraphEventApplicationService graphEvents;
    private final OpsChatRuntimeSettings settings;
    private final OpsChatTimeoutResponseFactory timeoutResponseFactory;

    OpsChatSyncExecutionCoordinator(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
            GraphEventApplicationService graphEvents,
            OpsChatRuntimeSettings settings) {
        this.executeWorkSession = executeWorkSession;
        this.graphEvents = graphEvents;
        this.settings = settings == null ? OpsChatRuntimeSettings.defaults() : settings;
        this.timeoutResponseFactory = new OpsChatTimeoutResponseFactory(this.settings);
    }

    OpsAgentChatResponse execute(OpsAgentChatRequest request) {
        return execute(request, null);
    }

    OpsAgentChatResponse execute(
            OpsAgentChatRequest request,
            Consumer<OpsAgentChatResponse> lateCompletion) {
        if (request == null || settings.syncTimeoutSeconds() <= 0) {
            return executeWorkSession.execute(request);
        }
        CompletableFuture<OpsAgentChatResponse> future = CompletableFuture.supplyAsync(
                () -> executeWorkSession.execute(request));
        try {
            return future.get(settings.syncTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            publishTimeout(request);
            registerLateCompletion(request, future, lateCompletion);
            return timeoutResponseFactory.create(request);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WORK_SESSION_INTERRUPTED", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(
                    "WORK_SESSION_EXECUTION_FAILED",
                    cause == null ? error : cause);
        }
    }

    private void registerLateCompletion(
            OpsAgentChatRequest request,
            CompletableFuture<OpsAgentChatResponse> future,
            Consumer<OpsAgentChatResponse> lateCompletion) {
        if (future == null || lateCompletion == null) return;
        future.whenComplete((response, failure) -> {
            if (failure != null || response == null) return;
            try {
                lateCompletion.accept(response);
            } catch (RuntimeException callbackFailure) {
                String runId = value(request == null ? null : request.getRunId());
                if (runId != null) {
                    graphEvents.publishRunEvent(
                            runId,
                            "main-agent",
                            "WORK_SESSION_LATE_COMPLETION_CALLBACK_FAILED",
                            "FAILED",
                            "后台 Work Session 完成后的收尾处理失败，主任务结果保持不变。");
                }
            }
        });
    }

    private void publishTimeout(OpsAgentChatRequest request) {
        String runId = value(request.getRunId());
        if (runId == null) {
            return;
        }
        graphEvents.publishRunEvent(
                runId,
                "main-agent",
                "WORK_SESSION_TIMEOUT",
                "TIMEOUT",
                "同步请求已超时返回，Work Session 仍在后台执行，可按 runId 恢复查看。");
    }

    private String value(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
