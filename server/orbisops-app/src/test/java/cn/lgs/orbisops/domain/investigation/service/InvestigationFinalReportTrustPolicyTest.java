package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationFinalReportTrustDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.lgs.orbisops.domain.investigation.model.InvestigationFinalReportTrustDecision.Failure.MISSING_SOURCE_OR_GAP_SECTION;
import static cn.lgs.orbisops.domain.investigation.model.InvestigationFinalReportTrustDecision.Failure.UNEXECUTED_SOURCE_CLAIM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationFinalReportTrustPolicyTest {

    private final InvestigationFinalReportTrustPolicy policy =
            new InvestigationFinalReportTrustPolicy();

    @Test
    void rejectsReportWithoutDatasourceAndGapSections() {
        InvestigationFinalReportTrustDecision decision = policy.assess(input(
                "## 结论\n日志显示存在异常。",
                evidence(true, false, false),
                List.of(result("elasticsearch", "FOUND", false))));

        assertFalse(decision.trusted());
        assertEquals(MISSING_SOURCE_OR_GAP_SECTION, decision.failure());
        assertEquals(List.of(), decision.violations());
        assertEquals(List.of("elasticsearch"), decision.executedSources().stream().toList());
    }

    @Test
    void acceptsExecutedEvidenceAndNegativeUnqueriedDatasourceStatement() {
        String report = """
                ## 关键证据
                日志显示 ERR_LOCK_001 集中出现。
                ## 数据源与缺口
                Elasticsearch 已查询；缺口：Prometheus 未查询。
                """;

        InvestigationFinalReportTrustDecision decision = policy.assess(input(
                report,
                evidence(true, false, false),
                List.of(result("elk-agent", "FOUND", false))));

        assertTrue(decision.trusted());
        assertEquals(InvestigationFinalReportTrustDecision.Failure.NONE, decision.failure());
        assertEquals(List.of("elasticsearch"), decision.executedSources().stream().toList());
        assertThrows(
                UnsupportedOperationException.class,
                () -> decision.executedSources().add("prometheus"));
    }

    @Test
    void rejectsClaimForDatasourceWithoutExecutionEvidence() {
        String report = """
                ## 关键证据
                Prometheus 查询结果显示 CPU 使用率升高。
                ## 数据源与缺口
                Elasticsearch 已查询；缺口：暂未补充其他证据。
                """;

        InvestigationFinalReportTrustDecision decision = policy.assess(input(
                report,
                evidence(true, false, false),
                List.of(result("elasticsearch", "FOUND", false))));

        assertFalse(decision.trusted());
        assertEquals(UNEXECUTED_SOURCE_CLAIM, decision.failure());
        assertEquals(List.of("prometheus"), decision.violations());
        assertEquals(List.of("elasticsearch"), decision.executedSources().stream().toList());
    }

    @Test
    void structuralEvidenceAndNonSkippedInvestigationResultsAuthorizeClaims() {
        String report = """
                ## 关键证据
                指标显示实例 CPU 正常。
                知识库检索到故障案例。
                ## 数据源与缺口
                Prometheus 与 RAG 已查询；缺口：未读取实时日志。
                """;

        InvestigationFinalReportTrustDecision decision = policy.assess(input(
                report,
                evidence(false, true, false),
                List.of(result("knowledge-vector", "ERROR", true))));

        assertTrue(decision.trusted());
        assertEquals(
                List.of("prometheus", "rag"),
                decision.executedSources().stream().toList());
    }

    @Test
    void skippedInvestigationDoesNotAuthorizeDatasourceClaim() {
        String report = """
                ## 关键证据
                知识库检索到历史故障案例。
                ## 数据源与缺口
                RAG 已查询；缺口：无。
                """;

        InvestigationFinalReportTrustDecision decision = policy.assess(input(
                report,
                evidence(false, false, false),
                List.of(result("rag", "SKIPPED", false))));

        assertFalse(decision.trusted());
        assertEquals(UNEXECUTED_SOURCE_CLAIM, decision.failure());
        assertEquals(List.of("rag"), decision.violations());
        assertTrue(decision.executedSources().isEmpty());
    }

    private InvestigationFinalReportTrustPolicy.Input input(
            String report,
            InvestigationFinalReportTrustPolicy.Evidence evidence,
            List<InvestigationFinalReportTrustPolicy.Result> results) {
        return new InvestigationFinalReportTrustPolicy.Input(report, evidence, results);
    }

    private InvestigationFinalReportTrustPolicy.Evidence evidence(
            boolean elasticsearch,
            boolean prometheus,
            boolean mysqlSlowSql) {
        return new InvestigationFinalReportTrustPolicy.Evidence(
                elasticsearch,
                prometheus,
                mysqlSlowSql);
    }

    private InvestigationFinalReportTrustPolicy.Result result(
            String source,
            String status,
            boolean hasGaps) {
        return new InvestigationFinalReportTrustPolicy.Result(
                source,
                status,
                hasGaps);
    }
}
