package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Spring facade for datasource SubAgent THINK/REVIEW decisions.
 * LLM protocol and deterministic fallback policies live behind plain collaborators.
 */
@Slf4j
@Service
public class OpsSubAgentDecisionService {

    private static final String DEFAULT_QUERY_FALLBACK_REASON =
            "沿用主 Agent 默认参数，并按问题中的结构化过滤条件收敛查询。";

    private final OpsSubAgentDecisionProtocolService protocolService;
    private final OpsSubAgentFallbackDecisionService fallbackDecisionService;
    private final OpsSubAgentDecisionSettings settings;

    public OpsSubAgentDecisionService(OpsAgentLlmClient llmClient) {
        this(llmClient, OpsSubAgentDecisionSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsSubAgentDecisionService(
            OpsAgentLlmClient llmClient,
            OpsSubAgentDecisionSettings settings) {
        this.protocolService = new OpsSubAgentDecisionProtocolService(llmClient);
        this.fallbackDecisionService = new OpsSubAgentFallbackDecisionService();
        this.settings = settings == null
                ? OpsSubAgentDecisionSettings.defaults()
                : settings;
    }

    public OpsSubAgentDecision decide(
            String source,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsQuestionContext questionContext,
            String datasourceProfile) {
        return decide(source, task, request, questionContext, datasourceProfile, "");
    }

    public OpsSubAgentDecision decide(
            String source,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsQuestionContext questionContext,
            String datasourceProfile,
            String previousObservation) {
        OpsSubAgentDecision fallback = fallbackDecisionService.query(
                source,
                request,
                questionContext,
                DEFAULT_QUERY_FALLBACK_REASON);
        if (!settings.decisionLlmEnabled()) {
            return fallback;
        }
        try {
            OpsSubAgentDecisionProtocolService.DecisionAttempt attempt =
                    protocolService.decide(new OpsSubAgentDecisionProtocolService.DecisionInput(
                            source,
                            task,
                            request,
                            questionContext,
                            datasourceProfile,
                            previousObservation,
                            fallback));
            if (attempt.valid()) {
                return attempt.decision();
            }
            if (attempt.status() == OpsSubAgentDecisionProtocolService.Status.NO_JSON) {
                return fallbackDecisionService.query(
                        source,
                        request,
                        questionContext,
                        "LLM THINK 未返回 JSON，使用只读默认查询策略。");
            }
            return fallbackDecisionService.query(
                    source,
                    request,
                    questionContext,
                    "LLM THINK JSON 校验失败，使用只读默认查询策略："
                            + attempt.validationErrors());
        } catch (OpsLlmDegradationException e) {
            log.warn("{} THINK 失败，使用只读默认查询策略：{}", source, e.getMessage());
            return fallbackDecisionService.query(
                    source,
                    request,
                    questionContext,
                    e.getMessage());
        }
    }

    public OpsAgentReview review(
            String source,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsQuestionContext questionContext,
            String observation,
            OpsAgentReview fallback) {
        if (!settings.reflectionLlmEnabled()) {
            return fallback;
        }
        try {
            OpsSubAgentDecisionProtocolService.ReviewAttempt attempt =
                    protocolService.review(new OpsSubAgentDecisionProtocolService.ReviewInput(
                            source,
                            task,
                            request,
                            questionContext,
                            observation,
                            fallback));
            if (attempt.valid()) {
                return attempt.review();
            }
            if (attempt.status() == OpsSubAgentDecisionProtocolService.Status.NO_JSON) {
                return fallbackDecisionService.review(
                        fallback,
                        "LLM REVIEW 未返回 JSON，使用基于真实 observation 的确定性判断。");
            }
            return fallbackDecisionService.review(
                    fallback,
                    "LLM REVIEW JSON 校验失败，使用基于真实 observation 的确定性判断："
                            + attempt.validationErrors());
        } catch (OpsLlmDegradationException e) {
            log.warn("{} REVIEW 失败，使用确定性 observation 判断：{}", source, e.getMessage());
            return fallbackDecisionService.review(fallback, e.getMessage());
        }
    }
}
