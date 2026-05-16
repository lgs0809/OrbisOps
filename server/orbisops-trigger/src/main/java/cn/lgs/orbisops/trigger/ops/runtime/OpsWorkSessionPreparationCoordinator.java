package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.trigger.application.runtime.OpsTaskContextAdapter;
import cn.lgs.orbisops.trigger.ops.OpsTelemetryService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Prepares one authoritative Work Session before engine invocation. */
@Slf4j
final class OpsWorkSessionPreparationCoordinator {

    private final OpsWorkSessionLeaseHeartbeat leaseHeartbeat;
    private final OpsAgentRuntimeRuleRouter ruleRouter;
    private final OpsTelemetryService telemetryService;
    private final OpsWorkSessionRunAdapter workSessionRunService;
    private final OpsWorkSessionRequestControl requestControl;
    private final OpsAnalysisRuntimeStateManager analysisStateManager;
    private final OpsRuntimeEventJournal runtimeEventJournal;
    private final OpsRuntimeConversationContextCoordinator conversationContextCoordinator;
    private final Supplier<OpsTaskContextAdapter> taskContextServiceSupplier;
    private final OpsAgentRunExecutionContextFactory executionContextFactory =
            new OpsAgentRunExecutionContextFactory();

    OpsWorkSessionPreparationCoordinator(OpsWorkSessionRuntimeAssembly assembly) {
        this(assembly, new OpsWorkSessionLeaseHeartbeat(assembly.workSessionRunService()));
    }

    OpsWorkSessionPreparationCoordinator(OpsWorkSessionRuntimeAssembly assembly,
                                         OpsWorkSessionLeaseHeartbeat leaseHeartbeat) {
        this.leaseHeartbeat = leaseHeartbeat;
        this.ruleRouter = assembly.ruleRouter();
        this.telemetryService = assembly.telemetryService();
        this.workSessionRunService = assembly.workSessionRunService();
        this.requestControl = assembly.requestControl();
        this.analysisStateManager = assembly.analysisStateManager();
        this.runtimeEventJournal = assembly.runtimeEventJournal();
        this.conversationContextCoordinator = assembly.conversationContextCoordinator();
        this.taskContextServiceSupplier = assembly.taskContextServiceSupplier();
    }

    OpsWorkSessionRuntimeContext prepare(
            OpsAgentChatRequest request,
            Consumer<OpsRuntimeEvent> eventSink) {
        OpsAgentChatRequest safeRequest = requestControl.normalize(request);
        long startedNanos = System.nanoTime();
        runtimeEventJournal.markRequestStarted(safeRequest, startedNanos);
        requestControl.assertNotCanceled(safeRequest);
        Consumer<OpsRuntimeEvent> persistentEventSink =
                runtimeEventJournal.persistentSink(safeRequest, eventSink);
        OpsWorkSessionRuntimeContext context = new OpsWorkSessionRuntimeContext(
                safeRequest,
                persistentEventSink,
                startedNanos);
        List<OpsRuntimeEvent> events = context.events();
        runtimeEventJournal.record(events, persistentEventSink, OpsRuntimeEvent.builder()
                .eventType("RUN_ACCEPTED")
                .status("RUNNING")
                .summary("Agent 任务已接收，开始解析定义和运行计划。")
                .payload(Map.of(
                        "elapsedMs",
                        0L,
                        "runId",
                        value(safeRequest.getRunId())))
                .build());
        OpsAgentDefinition definition = requestControl.resolveDefinition(safeRequest);
        requestControl.assertNotCanceled(safeRequest);
        OpsRuntimeExecutionPlan plan = ruleRouter.plan(safeRequest, definition);
        context.planned(definition, plan);
        AgentRunExecutionContext authority = executionContextFactory.bindServerContext(safeRequest, definition);
        OpsExecutionHarness executionHarness = OpsExecutionHarness.forRequest(safeRequest);
        try {
            workSessionRunService.begin(
                    safeRequest,
                    definition,
                    plan,
                    executionHarness);
        } catch (RuntimeException error) {
            log.error(
                    "Work Session begin failed, runId={}, projectId={}, harness={}, stage={}, triggerSource={}, approvedPackageBound={}, error={}",
                    value(safeRequest.getRunId()),
                    value(safeRequest.getProjectId()),
                    executionHarness.name(),
                    authority.stage().name(),
                    authority.triggerSource().name(),
                    authority.approvedPackage().isPresent(),
                    value(error.getMessage()));
            throw error;
        }
        // The durable claim already exists here. Question rewriting and memory retrieval can
        // block as long as engine calls, so event-driven renewal alone is insufficient.
        try (OpsWorkSessionLeaseHeartbeat.Scope heartbeat = leaseHeartbeat.start(safeRequest)) {
            try {
                OpsTaskContextAdapter taskContextService = taskContextServiceSupplier.get();
                if (taskContextService != null) {
                    taskContextService.startRun(safeRequest, definition);
                }
                telemetryService.recordRuntimeStarted(
                        definition.getAgentId(),
                        context.engine());
                runtimeEventJournal.record(events, persistentEventSink, OpsRuntimeEvent.builder()
                        .eventType("RUNTIME_PLANNED")
                        .status("SUCCEEDED")
                        .summary("运行计划已生成，adapter=" + plan.getAdapterKey())
                        .payload(runtimeEventJournal.payloadWithElapsed(
                                plan.getMetadata(),
                                startedNanos))
                        .build());
                if (taskContextService != null) {
                    taskContextService.updateFromEvents(
                            safeRequest,
                            definition,
                            "RUNNING",
                            events,
                            "运行计划已生成。");
                }
                conversationContextCoordinator.prepareMainQuestion(
                        definition,
                        safeRequest,
                        plan,
                        events,
                        persistentEventSink,
                        startedNanos);
                heartbeat.assertHealthy();
                workSessionRunService.bindContextBundle(safeRequest);

                runtimeEventJournal.record(events, persistentEventSink, OpsRuntimeEvent.builder()
                        .eventType("RUN_STARTED")
                        .status("RUNNING")
                        .summary("Agent 任务开始执行，mode="
                                + context.mode()
                                + ", engine="
                                + context.engine()
                                + ", adapter="
                                + plan.getAdapterKey())
                        .payload(runtimeEventJournal.payloadWithElapsed(
                                plan.getMetadata(),
                                startedNanos))
                        .build());
                if (taskContextService != null) {
                    taskContextService.updateFromEvents(
                            safeRequest,
                            definition,
                            "RUNNING",
                            events,
                            "Agent 任务已开始执行。");
                }
                if (!analysisStateManager.isAnalysisRequest(safeRequest) && plan.isMemoryEnabled()) {
                    conversationContextCoordinator.appendMessage(
                            safeRequest,
                            definition,
                            events,
                            persistentEventSink,
                            startedNanos,
                            "user",
                            conversationContextCoordinator.userMemoryContent(safeRequest),
                            Map.of(
                                    "agentId",
                                    definition.getAgentId(),
                                    "originalQuestion",
                                    conversationContextCoordinator.originalUserQuery(safeRequest),
                                    "rewrittenQuestion",
                                    safeRequest.getQuery()));
                }
                heartbeat.assertHealthy();
            } catch (RuntimeException error) {
                heartbeat.assertHealthy();
                throw error;
            }
        } catch (RuntimeException error) {
            requestControl.finish(safeRequest, "FAILED", error.getMessage());
            requestControl.markFinished(safeRequest);
            throw error;
        }
        return context;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
