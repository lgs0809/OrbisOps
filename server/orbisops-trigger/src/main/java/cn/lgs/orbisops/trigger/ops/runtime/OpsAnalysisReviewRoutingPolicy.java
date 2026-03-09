package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Owns review decisions, review modes and main-loop bounds. */
final class OpsAnalysisReviewRoutingPolicy {

    static final String REVIEW_MODE_NONE = "none";
    static final String REVIEW_MODE_BATCH = "batch";
    static final String REVIEW_MODE_IMMEDIATE = "immediate";

    private final OpsAnalysisSourcePolicy sourcePolicy;

    OpsAnalysisReviewRoutingPolicy(OpsAnalysisSourcePolicy sourcePolicy) {
        this.sourcePolicy = sourcePolicy;
    }

    String reviewDecisionForRoutes(List<String> routes) {
        List<String> decisions = Optional.ofNullable(routes).orElse(List.of()).stream()
                .map(sourcePolicy::normalizeSource)
                .filter(StringUtils::hasText)
                .map(route -> "replan_required".equals(route)
                        ? route
                        : "needs:" + route)
                .distinct()
                .toList();
        return decisions.isEmpty() ? "final_report" : String.join(",", decisions);
    }

    String reviewMode(OpsAgentDefinition definition) {
        return Optional.ofNullable(definition == null ? null : definition.getNodes())
                .orElse(List.of()).stream()
                .filter(node -> "REVIEW".equals(executionNodeType(node)))
                .findFirst()
                .map(node -> reviewMode(definition, node))
                .orElse(REVIEW_MODE_NONE);
    }

    String reviewMode(OpsAgentDefinition definition, OpsWorkflowNode reviewNode) {
        Object configured = Optional.ofNullable(reviewNode)
                .map(OpsWorkflowNode::getConfig)
                .map(config -> config.get("reviewMode"))
                .orElse(null);
        if (configured == null
                && (reviewNode == null
                || !"REVIEW".equals(executionNodeType(reviewNode)))) {
            return reviewMode(definition);
        }
        if (configured == null) return REVIEW_MODE_IMMEDIATE;
        String mode = String.valueOf(configured).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(mode)) {
            return REVIEW_MODE_IMMEDIATE;
        }
        if (Set.of(
                "none", "off", "disabled", "no_review", "no-review", "不复盘")
                .contains(mode)) {
            return REVIEW_MODE_NONE;
        }
        if (Set.of("batch", "overall", "global", "整体复盘").contains(mode)) {
            return REVIEW_MODE_BATCH;
        }
        return REVIEW_MODE_IMMEDIATE;
    }

    int maxMainRounds(OpsAgentDefinition definition, OpsAgentRunRequestDTO request) {
        Integer loopConfigured = Optional.ofNullable(
                definition == null ? null : definition.getLoops())
                .orElse(List.of()).stream()
                .map(OpsLoopPolicy::getMaxRounds)
                .filter(rounds -> rounds != null && rounds > 0)
                .findFirst()
                .orElse(null);
        int configured = Optional.ofNullable(request)
                .map(OpsAgentRunRequestDTO::getMaxRounds)
                .orElse(Optional.ofNullable(loopConfigured)
                        .orElse(Optional.ofNullable(
                                definition == null
                                        ? null
                                        : definition.getDefaultMaxMainRounds())
                                .orElse(3)));
        return Math.max(1, Math.min(configured, 8));
    }

    private String executionNodeType(OpsWorkflowNode node) {
        if (node == null || !StringUtils.hasText(node.getType())) return "CHAT";
        return node.getType().trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
