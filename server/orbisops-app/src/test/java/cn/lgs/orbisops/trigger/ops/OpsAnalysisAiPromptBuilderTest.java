package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisAiPromptBuilderTest {

    private final OpsAnalysisAiPromptBuilder promptBuilder =
            new OpsAnalysisAiPromptBuilder();

    @Test
    void buildsOriginalPromptWithReportLogsSlowSqlAndOutputContract() {
        String longSql = "select * from orders where payload = '" + "x".repeat(260) + "'";
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .markdownReport("# 运维分析结果\n\n## 结论\n发现风险。")
                .recentLogs(List.of(
                        OpsAnalysisResponseDTO.LogSampleDTO.builder()
                                .level("ERROR")
                                .timestamp("2026-07-27T10:00:00Z")
                                .loggerName("OrderService")
                                .message("lock failed")
                                .build(),
                        OpsAnalysisResponseDTO.LogSampleDTO.builder()
                                .level("WARN")
                                .timestamp("2026-07-27T10:01:00Z")
                                .loggerName("PaymentService")
                                .message("retrying")
                                .build()))
                .slowSqlSamples(List.of(
                        OpsAnalysisResponseDTO.SlowSqlSampleDTO.builder()
                                .queryTimeMs(null)
                                .rowsExamined(null)
                                .countStar(null)
                                .sqlText(longSql)
                                .build(),
                        OpsAnalysisResponseDTO.SlowSqlSampleDTO.builder()
                                .queryTimeMs(800D)
                                .rowsExamined(2_000L)
                                .countStar(3L)
                                .sqlText("select 1")
                                .build()))
                .build();

        String prompt = promptBuilder.build(response);

        assertTrue(prompt.startsWith("你是生产业务系统的运维主 Agent。"));
        assertTrue(prompt.contains("不要把采集数据原样罗列成流水账"));
        assertTrue(prompt.contains("不要假装查过未选择的数据源"));
        assertTrue(prompt.contains("重点关注接口 QPS、响应时间、错误率、实例在线情况、日志 ERROR/WARN 和业务风险"));
        assertTrue(prompt.contains("# 运维分析结果\n\n## 结论\n发现风险。"));
        assertTrue(prompt.contains("- [ERROR] 2026-07-27T10:00:00Z OrderService：lock failed"));
        assertTrue(prompt.contains("- [WARN] 2026-07-27T10:01:00Z PaymentService：retrying"));
        assertTrue(prompt.contains("- timeMs=0, rowsExamined=0, count=0, sql="));
        assertTrue(prompt.contains("- timeMs=800.0, rowsExamined=2000, count=3, sql=select 1"));
        assertTrue(prompt.contains(longSql.substring(0, 240) + "..."));
        assertFalse(prompt.contains(longSql));
        assertTrue(prompt.endsWith("5. 本次分析使用了哪些真实数据源。\n"));

        assertOrdered(prompt,
                "# 运维分析结果",
                "### 最近日志样本",
                "### MySQL 慢 SQL 样本",
                "请输出：",
                "1. 运行状态结论。",
                "2. 关键数据解读。",
                "3. 异常或风险判断。",
                "4. 具体排查或优化建议。",
                "5. 本次分析使用了哪些真实数据源。");
    }

    @Test
    void preservesOriginalNullReportAndEmptySampleSemantics() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder().build();

        String prompt = promptBuilder.build(response);

        assertTrue(prompt.contains("\n\nnull\n### 最近日志样本\n\n"));
        assertTrue(prompt.contains("\n### MySQL 慢 SQL 样本\n\n\n请输出：\n"));
        assertFalse(prompt.contains("- ["));
        assertFalse(prompt.contains("- timeMs="));
    }

    private void assertOrdered(String value, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = value.indexOf(marker);
            assertTrue(current > previous, marker + " should follow previous marker");
            previous = current;
        }
    }
}
