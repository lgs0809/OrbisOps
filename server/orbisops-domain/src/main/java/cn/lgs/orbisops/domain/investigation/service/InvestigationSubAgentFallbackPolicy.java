package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationSubAgentQueryDecision;
import cn.lgs.orbisops.domain.investigation.model.InvestigationSubAgentReviewDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministic fallback policy for SubAgent THINK/REVIEW degradation. */
public final class InvestigationSubAgentFallbackPolicy {

    public InvestigationSubAgentQueryDecision query(QueryInput input) {
        String retrievalMode = input.runtimeFilterPresent() ? "hybrid" : "auto";
        return new InvestigationSubAgentQueryDecision(
                false,
                "只读兜底策略：" + input.reason(),
                input.rangeMinutes(),
                input.promWindow(),
                input.includeRecentLogs(),
                retrievalMode,
                input.source() + " 默认排障查询",
                input.runtimeFilterPresent(),
                List.of("真实查询结果", "证据缺口", "是否需要主 Agent 调整"));
    }

    public InvestigationSubAgentReviewDecision review(ReviewInput input) {
        List<String> gaps = new ArrayList<>();
        gaps.add("LLM 子 Agent 复盘不可用：" + input.reason());
        gaps.addAll(Optional.ofNullable(input.gaps()).orElse(List.of()));
        List<String> adjustments = new ArrayList<>();
        adjustments.add("已保留真实查询 observation，并使用确定性规则判断证据状态。");
        adjustments.addAll(Optional.ofNullable(input.suggestedAdjustments()).orElse(List.of()));
        return new InvestigationSubAgentReviewDecision(
                false,
                input.status(),
                input.summary(),
                gaps,
                adjustments,
                input.shouldRetry(),
                input.confidence());
    }

    public record QueryInput(
            String source,
            Integer rangeMinutes,
            String promWindow,
            Boolean includeRecentLogs,
            boolean runtimeFilterPresent,
            String reason) {
    }

    public record ReviewInput(
            String status,
            String summary,
            List<String> gaps,
            List<String> suggestedAdjustments,
            Boolean shouldRetry,
            Double confidence,
            String reason) {
    }
}
