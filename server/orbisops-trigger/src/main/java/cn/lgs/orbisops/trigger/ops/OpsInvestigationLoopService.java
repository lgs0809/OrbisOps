package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Unified application loop for initial Investigation and StateGraph follow-ups. */
final class OpsInvestigationLoopService {

    private final OpsInvestigationObservationRoutingService observationRoutingService;
    private final OpsInvestigationSubAgentExecutionService subAgentExecutionService;
    private final OpsInvestigationRetryService retryService;
    private final OpsInvestigationTaskQueue taskQueue;
    private final OpsInvestigationTaskResolver taskResolver;
    private final OpsInvestigationLoopNotes notes;

    OpsInvestigationLoopService(
            OpsInvestigationObservationRoutingService observationRoutingService,
            OpsInvestigationSubAgentExecutionService subAgentExecutionService,
            OpsInvestigationRetryService retryService,
            OpsInvestigationTaskQueue taskQueue,
            OpsInvestigationTaskResolver taskResolver,
            OpsInvestigationLoopNotes notes) {
        this.observationRoutingService = observationRoutingService;
        this.subAgentExecutionService = subAgentExecutionService;
        this.retryService = retryService;
        this.taskQueue = taskQueue;
        this.taskResolver = taskResolver;
        this.notes = notes;
    }

    Outcome executeInitial(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsQuestionContext questionContext,
            Settings settings) {
        return execute(new Input(
                Mode.INITIAL,
                request,
                response,
                plan,
                questionContext,
                List.of(),
                List.of(),
                Set.of(),
                "",
                settings));
    }

    Outcome executeFollowUps(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> seedResults,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> inspectionResults,
            Set<String> reservedSources,
            String label,
            Settings settings) {
        return execute(new Input(
                Mode.FOLLOW_UP,
                request,
                response,
                plan,
                questionContext,
                seedResults,
                inspectionResults,
                reservedSources,
                label,
                settings));
    }

    private Outcome execute(Input input) {
        OpsInvestigationLoopSession session = start(input);
        recordInitialNotes(input, session);
        inspectAvailableResults(input, session);
        drainQueue(input, session);
        recordExecutionLimit(input, session);
        return new Outcome(
                session.results,
                session.executionNotes,
                new HashSet<>(session.executedSources));
    }

    private OpsInvestigationLoopSession start(Input input) {
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results =
                new ArrayList<>(Optional.ofNullable(input.seedResults()).orElse(List.of()));
        Set<String> availableSources = results.stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsAnalysisResponseDTO.InvestigationResultDTO::getSource)
                .filter(this::hasText)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> executedSources = new HashSet<>(availableSources);
        Optional.ofNullable(input.reservedSources()).orElse(Set.of()).stream()
                .filter(this::hasText)
                .forEach(executedSources::add);
        Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue =
                input.mode() == Mode.INITIAL
                        ? taskQueue.create(input.plan().getTasks())
                        : new ArrayDeque<>();
        int maxAdjustments = Math.max(
                0,
                Math.min(
                        Optional.ofNullable(input.request().getMaxRounds()).orElse(2),
                        Math.max(0, input.settings().maxAdjustmentsLimit())));
        int maxExecutions = Math.max(1, input.settings().maxTaskExecutions());
        return new OpsInvestigationLoopSession(
                results,
                new ArrayList<>(),
                availableSources,
                executedSources,
                queue,
                maxAdjustments,
                maxExecutions);
    }

    private void recordInitialNotes(Input input, OpsInvestigationLoopSession session) {
        session.executionNotes.addAll(notes.initial(
                input.plan(),
                input.mode() == Mode.FOLLOW_UP,
                input.label(),
                session.availableSources));
    }

    private void inspectAvailableResults(Input input, OpsInvestigationLoopSession session) {
        if (input.mode() != Mode.FOLLOW_UP) return;
        for (OpsAnalysisResponseDTO.InvestigationResultDTO result :
                new ArrayList<>(Optional.ofNullable(input.inspectionResults()).orElse(List.of()))) {
            subAgentExecutionService.assertNotCanceled(input.request());
            if (result == null) continue;
            OpsAnalysisResponseDTO.InvestigationTaskDTO task =
                    taskResolver.resolve(input.plan(), result.getSource());
            OpsAnalysisResponseDTO.InvestigationResultDTO latestResult =
                    retryInspectedResult(input, session, task, result);
            reflectOrEnqueueRules(input, session, latestResult);
        }
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO retryInspectedResult(
            Input input,
            OpsInvestigationLoopSession session,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAnalysisResponseDTO.InvestigationResultDTO result) {
        Optional<OpsAgentRunRequestDTO> adjustedRequest =
                session.adjustments < session.maxAdjustments
                        ? retryService.adjustedRequest(input.request(), result)
                        : Optional.empty();
        if (adjustedRequest.isEmpty()) return result;

        OpsAgentRunRequestDTO retryRequest = adjustedRequest.get();
        session.executionNotes.add(notes.inspectedRetry(task, retryRequest));
        OpsAnalysisResponseDTO.InvestigationResultDTO latestResult =
                subAgentExecutionService.executeOne(
                        task,
                        retryRequest,
                        input.response(),
                        input.questionContext(),
                        input.settings().defaultMaxEvidenceItems());
        session.results.add(latestResult);
        session.executedSources.add(task.getSource());
        session.executionNotes.add(notes.inspectedRetryResult(task, latestResult));
        session.adjustments++;
        return latestResult;
    }

    private void drainQueue(Input input, OpsInvestigationLoopSession session) {
        while (!session.queue.isEmpty() && session.guard < session.maxExecutions) {
            subAgentExecutionService.assertNotCanceled(input.request());
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> batch =
                    taskQueue.pollNextPriorityBatch(
                            session.queue,
                            session.executedSources,
                            input.settings().parallelExecutionEnabled());
            if (batch.isEmpty()) continue;

            session.guard += batch.size();
            recordParallelBatchNote(input, session, batch);
            List<OpsInvestigationSubAgentExecutionService.TaskExecution> taskExecutions =
                    subAgentExecutionService.executeBatch(
                            batch,
                            input.request(),
                            input.response(),
                            input.questionContext(),
                            input.settings().defaultMaxEvidenceItems());
            recordTaskExecutions(input, session, taskExecutions);
            processTaskExecutions(input, session, taskExecutions);
        }
    }

    private void recordParallelBatchNote(
            Input input,
            OpsInvestigationLoopSession session,
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> batch) {
        if (batch.size() <= 1) return;
        session.executionNotes.add(notes.parallelBatch(
                input.mode() == Mode.FOLLOW_UP,
                batch));
    }

    private void recordTaskExecutions(
            Input input,
            OpsInvestigationLoopSession session,
            List<OpsInvestigationSubAgentExecutionService.TaskExecution> taskExecutions) {
        for (OpsInvestigationSubAgentExecutionService.TaskExecution taskExecution : taskExecutions) {
            subAgentExecutionService.assertNotCanceled(input.request());
            OpsAnalysisResponseDTO.InvestigationTaskDTO task = taskExecution.task();
            OpsAnalysisResponseDTO.InvestigationResultDTO result = taskExecution.result();
            session.results.add(result);
            session.executedSources.add(task.getSource());
            session.executionNotes.add(notes.taskResult(
                    input.mode() == Mode.FOLLOW_UP,
                    task,
                    result));
        }
    }

    private void processTaskExecutions(
            Input input,
            OpsInvestigationLoopSession session,
            List<OpsInvestigationSubAgentExecutionService.TaskExecution> taskExecutions) {
        for (OpsInvestigationSubAgentExecutionService.TaskExecution taskExecution : taskExecutions) {
            subAgentExecutionService.assertNotCanceled(input.request());
            OpsAnalysisResponseDTO.InvestigationTaskDTO task = taskExecution.task();
            OpsAnalysisResponseDTO.InvestigationResultDTO latestResult =
                    retryQueuedResult(input, session, task, taskExecution.result());
            reflectOrEnqueueRules(input, session, latestResult);
        }
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO retryQueuedResult(
            Input input,
            OpsInvestigationLoopSession session,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAnalysisResponseDTO.InvestigationResultDTO result) {
        Optional<OpsAgentRunRequestDTO> adjustedRequest =
                session.adjustments < session.maxAdjustments
                        ? retryService.adjustedRequest(input.request(), result)
                        : Optional.empty();
        if (adjustedRequest.isEmpty()) return result;

        OpsAgentRunRequestDTO retryRequest = adjustedRequest.get();
        session.executionNotes.add(notes.queuedRetry(
                input.mode() == Mode.FOLLOW_UP,
                task,
                retryRequest));
        OpsAnalysisResponseDTO.InvestigationResultDTO latestResult =
                subAgentExecutionService.executeOne(
                        task,
                        retryRequest,
                        input.response(),
                        input.questionContext(),
                        input.settings().defaultMaxEvidenceItems());
        session.results.add(latestResult);
        session.executionNotes.add(notes.retryResult(
                input.mode() == Mode.FOLLOW_UP,
                task,
                latestResult));
        session.adjustments++;
        return latestResult;
    }

    private void reflectOrEnqueueRules(
            Input input,
            OpsInvestigationLoopSession session,
            OpsAnalysisResponseDTO.InvestigationResultDTO latestResult) {
        OpsInvestigationObservationRoutingService.Decision decision =
                observationRoutingService.decide(
                        new OpsInvestigationObservationRoutingService.Input(
                                input.request(),
                                input.plan(),
                                latestResult,
                                session.results,
                                subAgentExecutionService.registeredSources(),
                                session.executedSources,
                                taskQueue.queuedSources(session.queue),
                                input.questionContext(),
                                input.settings().mainReflectionLlmEnabled(),
                                input.settings().mysqlSlowSqlEnabled()));
        if (decision.clearQueue()) {
            session.queue.clear();
        }
        decision.tasks().forEach(session.queue::addLast);
        session.executionNotes.addAll(decision.notes());
    }

    private void recordExecutionLimit(Input input, OpsInvestigationLoopSession session) {
        if (session.queue.isEmpty()) return;
        session.executionNotes.add(notes.executionLimit(
                input.mode() == Mode.FOLLOW_UP,
                session.maxExecutions,
                taskQueue.remainingSources(session.queue)));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    record Settings(
            boolean mainReflectionLlmEnabled,
            boolean parallelExecutionEnabled,
            int maxTaskExecutions,
            int maxAdjustmentsLimit,
            int defaultMaxEvidenceItems,
            boolean mysqlSlowSqlEnabled) {
    }

    record Outcome(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            List<String> executionNotes,
            Set<String> executedSources) {
    }

    private record Input(
            Mode mode,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> seedResults,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> inspectionResults,
            Set<String> reservedSources,
            String label,
            Settings settings) {
    }

    private enum Mode {
        INITIAL,
        FOLLOW_UP
    }

}
