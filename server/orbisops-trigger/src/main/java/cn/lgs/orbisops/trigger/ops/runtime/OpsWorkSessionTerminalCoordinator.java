package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.runtime.OpsTaskContextAdapter;
import cn.lgs.orbisops.trigger.ops.OpsTelemetryService;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Adapts success, cancellation and failure into one terminal Work Session protocol. */
final class OpsWorkSessionTerminalCoordinator {

    private final OpsTelemetryService telemetryService;
    private final OpsWorkSessionRequestControl requestControl;
    private final OpsWorkSessionFinalizer workSessionFinalizer;
    private final OpsAnalysisRuntimeStateManager analysisStateManager;
    private final OpsRuntimeEventJournal runtimeEventJournal;
    private final OpsRuntimeConversationContextCoordinator conversationContextCoordinator;
    private final OpsRuntimeSkillLearningCoordinator skillLearningCoordinator;
    private final Supplier<OpsTaskContextAdapter> taskContextServiceSupplier;

    OpsWorkSessionTerminalCoordinator(OpsWorkSessionRuntimeAssembly assembly) {
        this.telemetryService = assembly.telemetryService();
        this.requestControl = assembly.requestControl();
        this.workSessionFinalizer = assembly.workSessionFinalizer();
        this.analysisStateManager = assembly.analysisStateManager();
        this.runtimeEventJournal = assembly.runtimeEventJournal();
        this.conversationContextCoordinator = assembly.conversationContextCoordinator();
        this.skillLearningCoordinator = assembly.skillLearningCoordinator();
        this.taskContextServiceSupplier = assembly.taskContextServiceSupplier();
    }

    OpsAgentChatResponse success(OpsWorkSessionRuntimeContext context) {
        requireExecuted(context);
        return workSessionFinalizer.success(
                finalizerContext(context),
                context.output(),
                finalizerHooks(context));
    }

    OpsAgentChatResponse waitingApproval(
            OpsWorkSessionRuntimeContext context,
            OpsWorkflowApprovalPendingException pending) {
        requirePrepared(context);
        return workSessionFinalizer.waitingApproval(
                finalizerContext(context),
                pending.nodeId(),
                pending.approvalId(),
                finalizerHooks(context));
    }

    OpsAgentChatResponse canceled(
            OpsWorkSessionRuntimeContext context,
            RuntimeException error) {
        requirePrepared(context);
        return workSessionFinalizer.canceled(
                finalizerContext(context),
                error == null ? "" : error.getMessage(),
                finalizerHooks(context));
    }

    void failed(
            OpsWorkSessionRuntimeContext context,
            RuntimeException error) {
        requirePrepared(context);
        workSessionFinalizer.failed(
                finalizerContext(context),
                error,
                finalizerHooks(context));
    }

    private OpsWorkSessionFinalizer.Context finalizerContext(
            OpsWorkSessionRuntimeContext context) {
        return new OpsWorkSessionFinalizer.Context(
                context.request(),
                context.definition(),
                context.mode(),
                context.engine(),
                context.events(),
                context.startedNanos(),
                analysisStateManager.isAnalysisRequest(context.request())
                        || context.plan() == null
                        || !context.plan().isMemoryEnabled());
    }

    private OpsWorkSessionFinalizer.Hooks finalizerHooks(
            OpsWorkSessionRuntimeContext context) {
        OpsAgentChatRequest request = context.request();
        OpsAgentDefinition definition = context.definition();
        String engine = context.engine();
        List<OpsRuntimeEvent> events = context.events();
        Consumer<OpsRuntimeEvent> eventSink = context.eventSink();
        long startedNanos = context.startedNanos();
        return new OpsWorkSessionFinalizer.Hooks() {
            @Override
            public void record(OpsRuntimeEvent event) {
                runtimeEventJournal.record(events, eventSink, event);
            }

            @Override
            public void appendAssistant(
                    String content,
                    Map<String, Object> metadata) {
                conversationContextCoordinator.appendMessage(
                        request,
                        definition,
                        events,
                        eventSink,
                        startedNanos,
                        "assistant",
                        content,
                        metadata);
            }

            @Override
            public void telemetrySucceeded(long durationMs) {
                telemetryService.recordRuntimeSucceeded(
                        definition.getAgentId(),
                        engine,
                        durationMs);
            }

            @Override
            public void telemetryFailed(long durationMs) {
                telemetryService.recordRuntimeFailed(
                        definition.getAgentId(),
                        engine,
                        durationMs);
            }

            @Override
            public void finishTaskContext(String status, String summary) {
                OpsTaskContextAdapter taskContextService =
                        taskContextServiceSupplier.get();
                if (taskContextService != null) {
                    taskContextService.finishRun(
                            request,
                            definition,
                            status,
                            summary,
                            events);
                }
            }

            @Override
            public void recordSkillUsage(String status) {
                skillLearningCoordinator.recordUsage(
                        request,
                        definition,
                        events,
                        status);
            }

            @Override
            public void finishDurable(
                    String status,
                    String error,
                    OpsAgentChatResponse response) {
                if (response == null) {
                    requestControl.finish(request, status, error);
                } else {
                    requestControl.finish(request, status, error, response);
                }
            }

            @Override
            public void markFinished() {
                requestControl.markFinished(request);
            }
        };
    }

    private void requirePrepared(OpsWorkSessionRuntimeContext context) {
        if (context == null
                || context.definition() == null
                || context.plan() == null) {
            throw new IllegalStateException("WORK_SESSION_NOT_PREPARED");
        }
    }

    private void requireExecuted(OpsWorkSessionRuntimeContext context) {
        requirePrepared(context);
        if (context.output() == null) {
            throw new IllegalStateException("WORK_SESSION_NOT_EXECUTED");
        }
    }
}
