package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsFinalReportTrustServiceTest {

    private final OpsFinalReportTrustService trustService =
            new OpsFinalReportTrustService();

    @Test
    void mapsStructuredAndInvestigationEvidenceIntoDomainTrustInput() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("report-trust")
                .endpointMetrics(List.of(
                        OpsAnalysisResponseDTO.EndpointMetricDTO.builder()
                                .uri("/api/orders")
                                .build()))
                .investigationResults(List.of(
                        OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                                .source("knowledge-vector")
                                .status("FOUND")
                                .gaps(List.of())
                                .build()))
                .build();
        String report = """
                ## 关键证据
                指标显示实例稳定。
                知识库检索到订单故障案例。
                ## 数据源与缺口
                Prometheus 和 RAG 已查询；缺口：未读取实时日志。
                """;

        assertTrue(trustService.trusted(response, report));
    }

    @Test
    void skippedResultAndMissingStructuredEvidenceDoNotAuthorizeClaim() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("report-trust-skipped")
                .investigationResults(List.of(
                        OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                                .source("mysql-slow-sql")
                                .status("SKIPPED")
                                .gaps(List.of("not selected"))
                                .build()))
                .build();
        String report = """
                ## 关键证据
                慢 SQL 显示订单表扫描行数较高。
                ## 数据源与缺口
                MySQL 已查询；缺口：无。
                """;

        assertFalse(trustService.trusted(response, report));
    }

    @Test
    void nonEmptySamplesAuthorizeDatasourceWithoutSummaryProjection() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("report-trust-samples")
                .recentLogs(List.of(
                        OpsAnalysisResponseDTO.LogSampleDTO.builder()
                                .message("ERR_LOCK_001")
                                .build()))
                .slowSqlSamples(List.of(
                        OpsAnalysisResponseDTO.SlowSqlSampleDTO.builder()
                                .sqlText("select * from orders")
                                .build()))
                .build();
        String report = """
                ## 关键证据
                日志显示锁单失败，慢 SQL 显示订单查询耗时较高。
                ## 数据源与缺口
                Elasticsearch 和 MySQL 已查询；缺口：未查询 Prometheus。
                """;

        assertTrue(trustService.trusted(response, report));
    }
}
