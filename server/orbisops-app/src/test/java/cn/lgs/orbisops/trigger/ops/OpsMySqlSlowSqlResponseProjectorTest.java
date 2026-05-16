package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQueryResult;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMySqlSlowSqlResponseProjectorTest {

    private final OpsMySqlSlowSqlResponseProjector projector =
            new OpsMySqlSlowSqlResponseProjector();

    @Test
    void projectMapsApplicationSamplesAndBuildsSummary() {
        MySqlSlowSqlQueryResult result = new MySqlSlowSqlQueryResult(
                true,
                " mysql.slow_log ",
                " query succeeded ",
                " range=30m ",
                List.of(
                        new MySqlSlowSqlSample(
                                "2026-07-27T00:00:00Z",
                                "demo_db",
                                "app@10.0.0.1",
                                "digest-1",
                                "select * from orders",
                                1_200.12D,
                                10_000L,
                                3L,
                                1L),
                        new MySqlSlowSqlSample(
                                "2026-07-27T00:01:00Z",
                                "demo_db",
                                "app@10.0.0.2",
                                "digest-2",
                                "update orders set status = 'PAID'",
                                600.12D,
                                null,
                                1L,
                                2L)));

        OpsMySqlSlowSqlResponseProjector.Projection projection = projector.project(result);

        assertTrue(projection.available());
        assertEquals("mysql.slow_log", projection.sourceName());
        assertEquals("query succeeded", projection.message());
        assertEquals("range=30m", projection.queryDescription());
        assertEquals(2, projection.samples().size());

        OpsAnalysisResponseDTO.SlowSqlSampleDTO first = projection.samples().get(0);
        assertEquals("2026-07-27T00:00:00Z", first.getStartTime());
        assertEquals("demo_db", first.getDatabaseName());
        assertEquals("app@10.0.0.1", first.getUserHost());
        assertEquals("digest-1", first.getDigest());
        assertEquals("select * from orders", first.getSqlText());
        assertEquals(Double.valueOf(1_200.12D), first.getQueryTimeMs());
        assertEquals(Long.valueOf(10_000L), first.getRowsExamined());
        assertEquals(Long.valueOf(3L), first.getRowsSent());
        assertEquals(Long.valueOf(1L), first.getCountStar());

        OpsAnalysisResponseDTO.SlowSqlSummaryDTO summary = projection.summary();
        assertEquals(Long.valueOf(2L), summary.getTotalStatements());
        assertEquals(Long.valueOf(2L), summary.getSlowStatements());
        assertEquals(Double.valueOf(900.12D), summary.getAvgQueryTimeMs());
        assertEquals(Double.valueOf(1_200.12D), summary.getMaxQueryTimeMs());
        assertEquals(Long.valueOf(10_000L), summary.getRowsExamined());
        assertThrows(
                UnsupportedOperationException.class,
                () -> projection.samples().add(first));
    }

    @Test
    void projectEmptyAndNullNumericSamplesUseOriginalZeroAggregation() {
        OpsMySqlSlowSqlResponseProjector.Projection empty = projector.project(
                new MySqlSlowSqlQueryResult(
                        false,
                        "performance_schema",
                        "unavailable",
                        "fallback",
                        List.of()));

        assertTrue(empty.samples().isEmpty());
        assertEquals(Long.valueOf(0L), empty.summary().getTotalStatements());
        assertEquals(Long.valueOf(0L), empty.summary().getSlowStatements());
        assertEquals(Double.valueOf(0D), empty.summary().getAvgQueryTimeMs());
        assertEquals(Double.valueOf(0D), empty.summary().getMaxQueryTimeMs());
        assertEquals(Long.valueOf(0L), empty.summary().getRowsExamined());

        OpsMySqlSlowSqlResponseProjector.Projection nullNumerics = projector.project(
                new MySqlSlowSqlQueryResult(
                        true,
                        "performance_schema",
                        "ok",
                        "digest fallback",
                        List.of(new MySqlSlowSqlSample(
                                "",
                                "",
                                "",
                                "digest",
                                "select 1",
                                null,
                                null,
                                null,
                                null))));

        assertEquals(Long.valueOf(1L), nullNumerics.summary().getTotalStatements());
        assertEquals(Double.valueOf(0D), nullNumerics.summary().getAvgQueryTimeMs());
        assertEquals(Double.valueOf(0D), nullNumerics.summary().getMaxQueryTimeMs());
        assertEquals(Long.valueOf(0L), nullNumerics.summary().getRowsExamined());
    }
}
