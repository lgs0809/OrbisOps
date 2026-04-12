package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpsInvestigationLoopServiceTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final OpsInvestigationLoopService.Settings SETTINGS =
            new OpsInvestigationLoopService.Settings(false, true, 8, 3, 12, true);

    @Test
    void initialModeExecutesPlanThroughSharedSessionLoop() {
        OpsSubAgent custom = agent("custom", (task, request, response, context) ->
                result(task, "FOUND", "initial evidence", false));
        OpsInvestigationLoopService service = service(List.of(custom));
        OpsAnalysisResponseDTO.InvestigationTaskDTO task = task("custom", "custom-agent");

        OpsInvestigationLoopService.Outcome outcome = service.executeInitial(
                request("run-initial", 10, "5m", 2),
                new OpsAnalysisResponseDTO(),
                plan(List.of(task)),
                OpsQuestionContext.from("custom evidence"),
                SETTINGS);

        assertEquals(1, outcome.results().size());
        assertEquals("initial evidence", outcome.results().get(0).getSummary());
        assertEquals(Set.of("custom"), outcome.executedSources());
        assertEquals("初始选择数据源：custom(reason)", outcome.executionNotes().get(0));
        assertTrue(outcome.executionNotes().contains("custom-agent 返回 FOUND：initial evidence"));
    }

    @Test
    void initialModeRetriesInTheSameSharedLoop() {
        AtomicInteger invocations = new AtomicInteger();
        List<Integer> ranges = new ArrayList<>();
        OpsSubAgent custom = agent("custom", (task, request, response, context) -> {
            ranges.add(request.getRangeMinutes());
            return invocations.getAndIncrement() == 0
                    ? result(task, "INSUFFICIENT", "need broader range", true)
                    : result(task, "FOUND", "retry evidence", false);
        });
        OpsInvestigationLoopService service = service(List.of(custom));

        OpsInvestigationLoopService.Outcome outcome = service.executeInitial(
                request("run-retry", 10, "5m", 1),
                new OpsAnalysisResponseDTO(),
                plan(List.of(task("custom", "custom-agent"))),
                OpsQuestionContext.from("custom evidence"),
                SETTINGS);

        assertEquals(List.of(10, 40), ranges);
        assertEquals(2, outcome.results().size());
        assertEquals("INSUFFICIENT", outcome.results().get(0).getStatus());
        assertEquals("FOUND", outcome.results().get(1).getStatus());
        assertTrue(outcome.executionNotes().stream()
                .anyMatch(note -> note.contains("rangeMinutes=40，promWindow=5m")));
    }

    @Test
    void followUpModePreservesSeedResultsAndReservedSources() {
        OpsInvestigationLoopService service = service(List.of());
        OpsAnalysisResponseDTO.InvestigationResultDTO seed =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("custom")
                        .agent("custom-agent")
                        .status("FOUND")
                        .summary("seed evidence")
                        .shouldRetry(false)
                        .build();

        OpsInvestigationLoopService.Outcome outcome = service.executeFollowUps(
                request("run-follow-up", 10, "5m", 2),
                new OpsAnalysisResponseDTO(),
                plan(List.of()),
                OpsQuestionContext.from("custom evidence"),
                List.of(seed),
                List.of(seed),
                Set.of("reserved"),
                "StateGraph 即时",
                SETTINGS);

        assertEquals(1, outcome.results().size());
        assertSame(seed, outcome.results().get(0));
        assertEquals(Set.of("custom", "reserved"), outcome.executedSources());
        assertEquals("StateGraph 即时 初始选择数据源：无", outcome.executionNotes().get(0));
        assertTrue(outcome.executionNotes().get(1).startsWith("StateGraph 即时 可用子 Agent 结果："));
    }

    @Test
    void followUpInspectionRetryAppendsResultWithoutReplacingSeed() {
        List<Integer> ranges = new ArrayList<>();
        OpsSubAgent custom = agent("custom", (task, request, response, context) -> {
            ranges.add(request.getRangeMinutes());
            return result(task, "FOUND", "follow-up retry evidence", false);
        });
        OpsInvestigationLoopService service = service(List.of(custom));
        OpsAnalysisResponseDTO.InvestigationResultDTO seed =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("custom")
                        .agent("custom-agent")
                        .status("INSUFFICIENT")
                        .summary("seed gap")
                        .shouldRetry(true)
                        .build();

        OpsInvestigationLoopService.Outcome outcome = service.executeFollowUps(
                request("run-follow-up-retry", 10, null, 1),
                new OpsAnalysisResponseDTO(),
                plan(List.of(task("custom", "custom-agent"))),
                OpsQuestionContext.from("custom evidence"),
                List.of(seed),
                List.of(seed),
                Set.of(),
                "StateGraph",
                SETTINGS);

        assertEquals(List.of(40), ranges);
        assertEquals(2, outcome.results().size());
        assertSame(seed, outcome.results().get(0));
        assertEquals("follow-up retry evidence", outcome.results().get(1).getSummary());
        assertTrue(outcome.executionNotes().stream()
                .anyMatch(note -> note.contains("证据不足，主 Agent 调整参数后重试：rangeMinutes=40，promWindow=null")));
    }

    private OpsInvestigationLoopService service(List<OpsSubAgent> agents) {
        OpsInvestigationSubAgentExecutionService executionService =
                new OpsInvestigationSubAgentExecutionService(
                        agents,
                        DIRECT_EXECUTOR,
                        new OpsRunCancellationRegistry());
        OpsInvestigationObservationRoutingService observationRoutingService =
                new OpsInvestigationObservationRoutingService(
                        new OpsInvestigationReflectionService(mock(OpsAgentLlmClient.class)),
                        new OpsInvestigationFollowUpService());
        return new OpsInvestigationLoopService(
                observationRoutingService,
                executionService,
                new OpsInvestigationRetryService(),
                new OpsInvestigationTaskQueue(),
                new OpsInvestigationTaskResolver(),
                new OpsInvestigationLoopNotes());
    }

    private OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan(
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        return OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .intent("RUNTIME_INVESTIGATION")
                .tasks(tasks)
                .conditionalTasks(List.of())
                .skippedTasks(List.of())
                .build();
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(String source, String agent) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(agent)
                .goal("goal")
                .reason("reason")
                .priority(1)
                .build();
    }

    private OpsAgentRunRequestDTO request(
            String runId,
            Integer rangeMinutes,
            String promWindow,
            Integer maxRounds) {
        return OpsAgentRunRequestDTO.builder()
                .runId(runId)
                .rangeMinutes(rangeMinutes)
                .promWindow(promWindow)
                .maxRounds(maxRounds)
                .nodeTimeoutSeconds(10)
                .maxEvidenceItems(12)
                .build();
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO result(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            String status,
            String summary,
            boolean shouldRetry) {
        return OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                .source(task.getSource())
                .agent(task.getAgent())
                .status(status)
                .summary(summary)
                .evidence(List.of())
                .attempts(List.of())
                .gaps(List.of())
                .suggestedAdjustments(List.of())
                .shouldRetry(shouldRetry)
                .confidence(1D)
                .build();
    }

    private OpsSubAgent agent(String source, InvestigationAction action) {
        return new OpsSubAgent() {
            @Override
            public String source() {
                return source;
            }

            @Override
            public OpsAnalysisResponseDTO.InvestigationResultDTO investigate(
                    OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                    OpsAgentRunRequestDTO request,
                    OpsAnalysisResponseDTO response,
                    OpsQuestionContext questionContext) {
                return action.execute(task, request, response, questionContext);
            }
        };
    }

    @FunctionalInterface
    private interface InvestigationAction {
        OpsAnalysisResponseDTO.InvestigationResultDTO execute(
                OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                OpsAgentRunRequestDTO request,
                OpsAnalysisResponseDTO response,
                OpsQuestionContext questionContext);
    }
}
