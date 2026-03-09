package cn.lgs.orbisops.trigger.ops;

import java.util.List;

/**
 * Post-action reflection from a datasource sub-agent.
 */
public record OpsAgentReview(boolean llmGenerated,
                             String status,
                             String summary,
                             List<String> gaps,
                             List<String> suggestedAdjustments,
                             Boolean shouldRetry,
                             Double confidence) {
}
