package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.List;
import java.util.Optional;

/** Deterministic Markdown fallback report rendering boundary. */
final class OpsFinalRuleReportRenderer {

    String render(
            OpsAnalysisResponseDTO response,
            String evidenceSnapshot,
            int maxChars) {
        StringBuilder report = new StringBuilder();
        report.append("# 运维分析结果\n\n");
        report.append("- 分析 ID：").append(response.getAnalysisId()).append('\n');
        report.append("- 生成时间：").append(response.getGeneratedAt()).append('\n');
        report.append("- 日志窗口：最近 ")
                .append(response.getRangeMinutes()).append(" 分钟\n");
        report.append("- PromQL 窗口：")
                .append(response.getPromWindow()).append("\n\n");

        appendConclusions(report, response);
        appendActions(report, response);
        appendSourcesAndGaps(report, response);
        report.append("\n## 证据快照\n\n");
        report.append(evidenceSnapshot);
        return truncate(report.toString(), maxChars);
    }

    private void appendConclusions(
            StringBuilder report,
            OpsAnalysisResponseDTO response) {
        report.append("## 结论\n\n");
        List<OpsAnalysisResponseDTO.InsightDTO> insights = insights(response);
        if (insights.isEmpty()) {
            report.append("- 暂未形成明确结论，需要补充实时数据或知识库证据。\n");
            return;
        }
        for (OpsAnalysisResponseDTO.InsightDTO insight : insights) {
            report.append("- [").append(insight.getLevel()).append("] ")
                    .append(insight.getTitle()).append("：")
                    .append(insight.getDetail()).append('\n');
        }
    }

    private void appendActions(
            StringBuilder report,
            OpsAnalysisResponseDTO response) {
        report.append("\n## 建议动作\n\n");
        List<OpsAnalysisResponseDTO.InsightDTO> insights = insights(response);
        if (insights.isEmpty()) {
            report.append("- 补充 traceId、orderId、接口 URI、时间窗口或告警名称后重新分析。\n");
            return;
        }
        for (OpsAnalysisResponseDTO.InsightDTO insight : insights) {
            report.append("- ").append(insight.getSuggestion()).append('\n');
        }
    }

    private void appendSourcesAndGaps(
            StringBuilder report,
            OpsAnalysisResponseDTO response) {
        report.append("\n## 数据源与缺口\n\n");
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results =
                Optional.ofNullable(response.getInvestigationResults())
                        .orElse(List.of());
        if (results.isEmpty()) {
            report.append("- 本轮没有子 Agent 返回调查结果。\n");
            return;
        }
        for (OpsAnalysisResponseDTO.InvestigationResultDTO result : results) {
            report.append("- ").append(result.getAgent()).append(" / ")
                    .append(result.getSource()).append("：")
                    .append(result.getStatus()).append("，")
                    .append(result.getSummary()).append('\n');
            Optional.ofNullable(result.getGaps())
                    .orElse(List.of())
                    .stream()
                    .limit(2)
                    .forEach(gap -> report
                            .append("  - 缺口：")
                            .append(gap)
                            .append('\n'));
        }
    }

    private List<OpsAnalysisResponseDTO.InsightDTO> insights(
            OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getInsights()).orElse(List.of());
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars))
                + "\n\n> 报告过长，已按配置截断。";
    }
}
