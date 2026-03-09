package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationInsight;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvestigationInsightPolicyTest {

    private final InvestigationInsightPolicy policy =
            new InvestigationInsightPolicy();

    @Test
    void emitsUnavailableInsightsInDatasourceOrder() {
        List<InvestigationInsight> insights = policy.assess(input(
                source(false, true, "prometheus down"),
                source(false, true, "es down"),
                source(false, true, "mysql down"),
                metrics(0, 0, 0D, 0D, 0D),
                logs(0L, 0L),
                slowSql(0L, 0D),
                false));

        assertEquals(List.of(
                        "Prometheus 数据不可用",
                        "Elasticsearch 数据不可用",
                        "MySQL 慢 SQL 数据不可用"),
                insights.stream().map(InvestigationInsight::title).toList());
        assertEquals("prometheus down", insights.get(0).detail());
        assertEquals("es down", insights.get(1).detail());
        assertEquals("mysql down", insights.get(2).detail());
    }

    @Test
    void emitsOperationalAnomaliesInOriginalOrderAndSeverity() {
        List<InvestigationInsight> insights = policy.assess(input(
                source(true, false, "ok"),
                source(true, false, "ok"),
                source(true, false, "ok"),
                metrics(3, 2, 0D, 6D, 81D),
                logs(12L, 2L),
                slowSql(1L, 4_000D),
                false));

        assertEquals(List.of(
                        "业务实例存在下线",
                        "接口错误率偏高",
                        "最近日志存在 ERROR",
                        "堆内存使用率较高",
                        "发现 MySQL 慢 SQL",
                        "当前业务流量较低"),
                insights.stream().map(InvestigationInsight::title).toList());
        assertEquals(List.of("HIGH", "HIGH", "WARN", "WARN", "HIGH", "INFO"),
                insights.stream().map(InvestigationInsight::level).toList());
        assertEquals("2/3 个实例处于 UP 状态。", insights.get(0).detail());
        assertEquals("最近 30 分钟 ERROR 日志 2 条。", insights.get(2).detail());
        assertEquals("命中 1 条慢 SQL，最高耗时约 4000.0ms。",
                insights.get(4).detail());
    }

    @Test
    void emitsWarningErrorRateBranchWithoutHighSeverity() {
        List<InvestigationInsight> insights = policy.assess(input(
                source(true, false, "ok"),
                source(false, false, null),
                source(false, false, null),
                metrics(1, 1, 2D, 2D, 20D),
                logs(0L, 0L),
                slowSql(0L, 0D),
                false));

        assertEquals(1, insights.size());
        assertEquals("WARN", insights.get(0).level());
        assertEquals("接口错误率需要关注", insights.get(0).title());
        assertEquals("当前 5xx 错误率约 2.0%。", insights.get(0).detail());
    }

    @Test
    void selectsOriginalFallbackInsightByAvailableEvidence() {
        assertEquals("业务系统当前状态平稳", title(input(
                source(true, false, "ok"),
                source(true, false, "ok"),
                source(false, false, null),
                healthyMetrics(),
                logs(0L, 0L),
                slowSql(0L, 0D),
                false)));
        assertEquals("监控指标未发现明显异常", title(input(
                source(true, false, "ok"),
                source(false, false, null),
                source(false, false, null),
                healthyMetrics(),
                logs(0L, 0L),
                slowSql(0L, 0D),
                false)));
        assertEquals("日志窗口未发现明显异常", title(input(
                source(false, false, null),
                source(true, false, "ok"),
                source(false, false, null),
                healthyMetrics(),
                logs(0L, 0L),
                slowSql(0L, 0D),
                false)));
        assertEquals("MySQL 慢 SQL 未发现明显异常", title(input(
                source(false, false, null),
                source(false, false, null),
                source(true, false, "ok"),
                healthyMetrics(),
                logs(0L, 0L),
                slowSql(0L, 0D),
                false)));
        assertEquals("实时数据已由子 Agent 查询", title(input(
                source(false, false, null),
                source(false, false, null),
                source(false, false, null),
                healthyMetrics(),
                logs(0L, 0L),
                slowSql(0L, 0D),
                true)));
        assertEquals("本轮未查询实时运行数据", title(input(
                source(false, false, null),
                source(false, false, null),
                source(false, false, null),
                healthyMetrics(),
                logs(0L, 0L),
                slowSql(0L, 0D),
                false)));
    }

    private String title(InvestigationInsightPolicy.Input input) {
        return policy.assess(input).get(0).title();
    }

    private InvestigationInsightPolicy.Input input(
            InvestigationInsightPolicy.Source prometheus,
            InvestigationInsightPolicy.Source elasticsearch,
            InvestigationInsightPolicy.Source mysql,
            InvestigationInsightPolicy.Metrics metrics,
            InvestigationInsightPolicy.Logs logs,
            InvestigationInsightPolicy.SlowSql slowSql,
            boolean realtimeResultQueried) {
        return new InvestigationInsightPolicy.Input(
                prometheus,
                elasticsearch,
                mysql,
                metrics,
                logs,
                slowSql,
                30,
                realtimeResultQueried);
    }

    private InvestigationInsightPolicy.Source source(
            boolean queried,
            boolean unavailable,
            String message) {
        return new InvestigationInsightPolicy.Source(
                queried,
                unavailable,
                message);
    }

    private InvestigationInsightPolicy.Metrics healthyMetrics() {
        return metrics(1, 1, 1D, 0D, 20D);
    }

    private InvestigationInsightPolicy.Metrics metrics(
            Integer total,
            Integer up,
            Double qps,
            Double errorRate,
            Double heap) {
        return new InvestigationInsightPolicy.Metrics(
                total,
                up,
                qps,
                errorRate,
                heap);
    }

    private InvestigationInsightPolicy.Logs logs(Long total, Long errors) {
        return new InvestigationInsightPolicy.Logs(total, errors);
    }

    private InvestigationInsightPolicy.SlowSql slowSql(
            Long statements,
            Double maxQueryTimeMs) {
        return new InvestigationInsightPolicy.SlowSql(
                statements,
                maxQueryTimeMs);
    }
}
