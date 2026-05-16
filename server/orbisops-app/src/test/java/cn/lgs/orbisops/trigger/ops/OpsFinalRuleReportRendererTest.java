package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsFinalRuleReportRendererTest {

    private final OpsFinalRuleReportRenderer renderer =
            new OpsFinalRuleReportRenderer();

    @Test
    void rendersOriginalRuleReportSectionsInsightsActionsAndTopTwoGaps() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("analysis-1")
                .generatedAt("2026-07-27 12:00:00")
                .rangeMinutes(30)
                .promWindow("5m")
                .insights(List.of(
                        OpsAnalysisResponseDTO.InsightDTO.builder()
                                .level("HIGH")
                                .title("错误率偏高")
                                .detail("5xx 为 8%。")
                                .suggestion("检查 ERROR 日志。")
                                .build(),
                        OpsAnalysisResponseDTO.InsightDTO.builder()
                                .level("WARN")
                                .title("存在慢 SQL")
                                .detail("最高耗时 3 秒。")
                                .suggestion("检查执行计划。")
                                .build()))
                .investigationResults(List.of(
                        OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                                .agent("mysql-agent")
                                .source("mysql")
                                .status("FOUND")
                                .summary("发现慢 SQL")
                                .gaps(List.of("gap-1", "gap-2", "gap-3"))
                                .build()))
                .build();

        String report = renderer.render(response, "snapshot-body", 10_000);

        assertTrue(report.startsWith("# 运维分析结果\n\n"));
        assertTrue(report.contains("- 分析 ID：analysis-1"));
        assertTrue(report.contains("- 生成时间：2026-07-27 12:00:00"));
        assertTrue(report.contains("- 日志窗口：最近 30 分钟"));
        assertTrue(report.contains("- PromQL 窗口：5m"));
        assertTrue(report.contains("- [HIGH] 错误率偏高：5xx 为 8%。"));
        assertTrue(report.contains("- [WARN] 存在慢 SQL：最高耗时 3 秒。"));
        assertTrue(report.contains("- 检查 ERROR 日志。"));
        assertTrue(report.contains("- 检查执行计划。"));
        assertTrue(report.contains("- mysql-agent / mysql：FOUND，发现慢 SQL"));
        assertTrue(report.contains("  - 缺口：gap-1"));
        assertTrue(report.contains("  - 缺口：gap-2"));
        assertFalse(report.contains("gap-3"));
        assertTrue(report.endsWith("## 证据快照\n\nsnapshot-body"));
        assertOrdered(report,
                "## 结论",
                "## 建议动作",
                "## 数据源与缺口",
                "## 证据快照");
    }

    @Test
    void rendersOriginalEmptyFallbackAndNullSnapshotSemantics() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder().build();

        String report = renderer.render(response, null, 10_000);

        assertTrue(report.contains(
                "- 暂未形成明确结论，需要补充实时数据或知识库证据。"));
        assertTrue(report.contains(
                "- 补充 traceId、orderId、接口 URI、时间窗口或告警名称后重新分析。"));
        assertTrue(report.contains("- 本轮没有子 Agent 返回调查结果。"));
        assertTrue(report.endsWith("## 证据快照\n\nnull"));
    }

    @Test
    void truncatesWithOriginalConfiguredSuffixIncludingNonPositiveLimit() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("analysis-long")
                .build();
        String suffix = "\n\n> 报告过长，已按配置截断。";

        String truncated = renderer.render(response, "snapshot", 40);
        String nonPositive = renderer.render(response, "snapshot", -5);

        assertTrue(truncated.endsWith(suffix));
        assertEquals(40 + suffix.length(), truncated.length());
        assertEquals(suffix, nonPositive);
    }

    private void assertOrdered(String value, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = value.indexOf(marker);
            assertTrue(current > previous, marker + " should follow previous section");
            previous = current;
        }
    }
}
