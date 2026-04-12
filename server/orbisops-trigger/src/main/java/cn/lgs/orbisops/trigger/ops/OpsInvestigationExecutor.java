package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Main operations agent entry adapter. Investigation loop state and transitions
 * are delegated to a plain application collaborator.
 */
@Service
public class OpsInvestigationExecutor {

    private final OpsInvestigationSubAgentExecutionService subAgentExecutionService;
    private final OpsInvestigationLoopService loopService;
    private final OpsInvestigationSkippedSourceProjector skippedSourceProjector;
    private final OpsInvestigationExecutorSettings executorSettings;
    private final OpsEsLogSettings esLogSettings;
    private final OpsPrometheusSettings prometheusSettings;
    private final OpsMySqlSlowSqlSettings mySqlSlowSqlSettings;

    public OpsInvestigationExecutor(
            List<OpsSubAgent> subAgents,
            OpsAgentLlmClient llmClient,
            @Qualifier("opsSubAgentExecutor") Executor opsSubAgentExecutor,
            OpsRunCancellationRegistry cancellationRegistry) {
        this(
                subAgents,
                llmClient,
                opsSubAgentExecutor,
                cancellationRegistry,
                OpsInvestigationExecutorSettings.defaults(),
                OpsEsLogSettings.defaults(),
                OpsPrometheusSettings.defaults(),
                OpsMySqlSlowSqlSettings.defaults());
    }

    @Autowired
    public OpsInvestigationExecutor(
            List<OpsSubAgent> subAgents,
            OpsAgentLlmClient llmClient,
            @Qualifier("opsSubAgentExecutor") Executor opsSubAgentExecutor,
            OpsRunCancellationRegistry cancellationRegistry,
            OpsInvestigationExecutorSettings executorSettings,
            OpsEsLogSettings esLogSettings,
            OpsPrometheusSettings prometheusSettings,
            OpsMySqlSlowSqlSettings mySqlSlowSqlSettings) {
        OpsInvestigationReflectionService reflectionService =
                new OpsInvestigationReflectionService(llmClient);
        OpsInvestigationFollowUpService followUpService =
                new OpsInvestigationFollowUpService();
        OpsInvestigationObservationRoutingService observationRoutingService =
                new OpsInvestigationObservationRoutingService(
                        reflectionService,
                        followUpService);
        this.subAgentExecutionService = new OpsInvestigationSubAgentExecutionService(
                subAgents,
                opsSubAgentExecutor,
                cancellationRegistry);
        OpsInvestigationRetryService retryService =
                new OpsInvestigationRetryService();
        OpsInvestigationTaskQueue taskQueue =
                new OpsInvestigationTaskQueue();
        OpsInvestigationTaskResolver taskResolver =
                new OpsInvestigationTaskResolver();
        this.loopService = new OpsInvestigationLoopService(
                observationRoutingService,
                subAgentExecutionService,
                retryService,
                taskQueue,
                taskResolver,
                new OpsInvestigationLoopNotes());
        this.skippedSourceProjector = new OpsInvestigationSkippedSourceProjector();
        this.executorSettings = executorSettings == null
                ? OpsInvestigationExecutorSettings.defaults()
                : executorSettings;
        this.esLogSettings = esLogSettings == null ? OpsEsLogSettings.defaults() : esLogSettings;
        this.prometheusSettings = prometheusSettings == null
                ? OpsPrometheusSettings.defaults()
                : prometheusSettings;
        this.mySqlSlowSqlSettings = mySqlSlowSqlSettings == null
                ? OpsMySqlSlowSqlSettings.defaults()
                : mySqlSlowSqlSettings;
    }

    public List<OpsAnalysisResponseDTO.InvestigationResultDTO> execute(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsQuestionContext questionContext) {
        OpsInvestigationLoopService.Outcome outcome = loopService.executeInitial(
                request,
                response,
                plan,
                questionContext,
                settings());
        skippedSourceProjector.project(
                response,
                outcome.executedSources(),
                plan,
                esLogSettings,
                prometheusSettings);
        response.setExecutionNotes(outcome.executionNotes());
        return outcome.results();
    }

    public OpsAnalysisResponseDTO.InvestigationResultDTO executeGraphSubAgent(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext) {
        return subAgentExecutionService.executeOne(
                task,
                request,
                response,
                questionContext,
                executorSettings.defaultMaxEvidenceItems());
    }

    public FollowUpOutcome executeGraphImmediateFollowUps(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> availableResults,
            OpsAnalysisResponseDTO.InvestigationResultDTO latestResult,
            Set<String> reservedSources) {
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> inspectionResults =
                latestResult == null ? List.of() : List.of(latestResult);
        return executeGraphFollowUpsInternal(
                request,
                response,
                plan,
                questionContext,
                availableResults,
                inspectionResults,
                reservedSources,
                false,
                "StateGraph 即时");
    }

    public FollowUpOutcome executeGraphFollowUps(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults) {
        return executeGraphFollowUpsInternal(
                request,
                response,
                plan,
                questionContext,
                initialResults,
                initialResults,
                Set.of(),
                true,
                "StateGraph");
    }

    private FollowUpOutcome executeGraphFollowUpsInternal(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> seedResults,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> inspectionResults,
            Set<String> reservedSources,
            boolean markSkipped,
            String label) {
        OpsInvestigationLoopService.Outcome outcome = loopService.executeFollowUps(
                request,
                response,
                plan,
                questionContext,
                seedResults,
                inspectionResults,
                reservedSources,
                label,
                settings());
        if (markSkipped) {
            skippedSourceProjector.project(
                    response,
                    outcome.executedSources(),
                    plan,
                    esLogSettings,
                    prometheusSettings);
        }
        return new FollowUpOutcome(outcome.results(), outcome.executionNotes());
    }

    private OpsInvestigationLoopService.Settings settings() {
        return new OpsInvestigationLoopService.Settings(
                executorSettings.mainReflectionLlmEnabled(),
                executorSettings.parallelExecutionEnabled(),
                executorSettings.maxTaskExecutions(),
                executorSettings.maxAdjustmentsLimit(),
                executorSettings.defaultMaxEvidenceItems(),
                mySqlSlowSqlSettings.enabled());
    }

    public record FollowUpOutcome(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            List<String> executionNotes) {
    }
}
