package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for operations sub-agents.
 */
public abstract class AbstractOpsSubAgent implements OpsSubAgent {

    protected static final String STATUS_FOUND = "FOUND";
    protected static final String STATUS_NOT_FOUND = "NOT_FOUND";
    protected static final String STATUS_INSUFFICIENT = "INSUFFICIENT";
    protected static final String STATUS_BLOCKED = "BLOCKED";
    protected static final String STATUS_ERROR = "ERROR";

    protected OpsAnalysisResponseDTO.InvestigationResultDTO result(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                           String status,
                                                                           String summary,
                                                                           List<String> evidence,
                                                                           List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts,
                                                                           List<String> gaps,
                                                                           List<String> suggestedAdjustments,
                                                                           Boolean shouldRetry,
                                                                           Double confidence) {
        return OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                .source(task.getSource())
                .agent(task.getAgent())
                .status(status)
                .summary(summary)
                .evidence(evidence == null ? new ArrayList<>() : evidence)
                .attempts(attempts == null ? new ArrayList<>() : attempts)
                .gaps(gaps == null ? new ArrayList<>() : gaps)
                .suggestedAdjustments(suggestedAdjustments == null ? new ArrayList<>() : suggestedAdjustments)
                .shouldRetry(shouldRetry)
                .confidence(confidence)
                .build();
    }

    protected OpsAnalysisResponseDTO.InvestigationAttemptDTO attempt(String query, Integer resultCount, String reason) {
        return OpsAnalysisResponseDTO.InvestigationAttemptDTO.builder()
                .query(query)
                .resultCount(resultCount)
                .reason(reason)
                .build();
    }

    protected OpsAnalysisResponseDTO.DataSourceStatusDTO status(String name, String url, Boolean available, String message) {
        return OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .name(name)
                .url(url)
                .available(available)
                .message(message)
                .build();
    }

    protected int subAgentMaxIterations(OpsAgentRunRequestDTO request) {
        Integer configured = request == null ? null : request.getSubAgentMaxIterations();
        int value = configured == null ? 3 : configured;
        return Math.max(1, Math.min(value, 10));
    }

    protected boolean shouldRetryWithinSubAgent(OpsAnalysisResponseDTO.InvestigationResultDTO result) {
        if (result == null || !Boolean.TRUE.equals(result.getShouldRetry())) {
            return false;
        }
        return STATUS_NOT_FOUND.equals(result.getStatus()) || STATUS_INSUFFICIENT.equals(result.getStatus());
    }

    protected OpsAnalysisResponseDTO.InvestigationResultDTO withLoopAggregates(OpsAnalysisResponseDTO.InvestigationResultDTO result,
                                                                                       List<String> evidence,
                                                                                       List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts,
                                                                                       List<String> gaps,
                                                                                       List<String> suggestedAdjustments,
                                                                                       boolean retryExhausted,
                                                                                       int maxIterations) {
        if (result == null) {
            return null;
        }
        result.setEvidence(evidence == null ? new ArrayList<>() : evidence);
        result.setAttempts(attempts == null ? new ArrayList<>() : attempts);
        result.setGaps(gaps == null ? new ArrayList<>() : gaps);
        result.setSuggestedAdjustments(suggestedAdjustments == null ? new ArrayList<>() : suggestedAdjustments);
        if (retryExhausted) {
            result.setShouldRetry(false);
            result.getSuggestedAdjustments().add("子 Agent 已达到最大循环次数 " + maxIterations + "，停止继续重试该数据源。");
        }
        return result;
    }

    protected <T> void addAll(List<T> target, List<T> source) {
        if (target != null && source != null && !source.isEmpty()) {
            target.addAll(source);
        }
    }

    protected String loopObservation(OpsAnalysisResponseDTO.InvestigationResultDTO result,
                                     int iteration,
                                     int maxIterations) {
        if (result == null) {
            return "";
        }
        return "iteration=" + iteration + "/" + maxIterations
                + "\nstatus=" + value(result.getStatus())
                + "\nsummary=" + value(result.getSummary())
                + "\nevidence=" + String.join(" | ", result.getEvidence() == null ? List.of() : result.getEvidence())
                + "\ngaps=" + String.join(" | ", result.getGaps() == null ? List.of() : result.getGaps())
                + "\nsuggestedAdjustments=" + String.join(" | ", result.getSuggestedAdjustments() == null ? List.of() : result.getSuggestedAdjustments());
    }

    protected boolean containsAny(String value, String... keywords) {
        if (value == null) {
            return false;
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lowered.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    protected String value(Object value) {
        return value == null ? "0" : String.valueOf(value);
    }

    protected double round(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    protected String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

}
