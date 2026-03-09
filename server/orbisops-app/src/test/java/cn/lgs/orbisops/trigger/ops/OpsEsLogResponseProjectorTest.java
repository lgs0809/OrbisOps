package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsEsLogResponseProjectorTest {

    private final OpsEsLogResponseProjector projector = new OpsEsLogResponseProjector();

    @Test
    void shouldProjectSummaryBucketsAndRecentSamples() {
        OpsEsLogQueryProtocolService.Result source = new OpsEsLogQueryProtocolService.Result(
                5L,
                Map.of("ERROR", 2L, "WARN", 1L, "INFO", 2L),
                List.of(new OpsEsLogQueryProtocolService.Bucket("cn.example.OrderService", 4L)),
                List.of(new OpsEsLogQueryProtocolService.LogSample(
                        "2026-07-28T08:00:00Z",
                        "ERROR",
                        "cn.example.OrderService",
                        "order create failed")));

        OpsEsLogResponseProjector.Projection projection = projector.project(source);

        assertAll(
                () -> assertEquals(5L, projection.summary().getTotalLogs()),
                () -> assertEquals(2L, projection.summary().getErrorLogs()),
                () -> assertEquals(1L, projection.summary().getWarnLogs()),
                () -> assertEquals(3, projection.summary().getLevelCounts().size()),
                () -> assertEquals(1, projection.summary().getTopLoggers().size()),
                () -> assertEquals("cn.example.OrderService", projection.summary().getTopLoggers().get(0).getKey()),
                () -> assertEquals(4L, projection.summary().getTopLoggers().get(0).getCount()),
                () -> assertEquals(1, projection.recentLogs().size()),
                () -> assertEquals("2026-07-28T08:00:00Z", projection.recentLogs().get(0).getTimestamp()),
                () -> assertEquals("ERROR", projection.recentLogs().get(0).getLevel()),
                () -> assertEquals("cn.example.OrderService", projection.recentLogs().get(0).getLoggerName()),
                () -> assertEquals("order create failed", projection.recentLogs().get(0).getMessage()));
    }

    @Test
    void nullResultShouldProjectEmptyStableResponse() {
        OpsEsLogResponseProjector.Projection projection = projector.project(null);

        assertAll(
                () -> assertEquals(0L, projection.summary().getTotalLogs()),
                () -> assertEquals(0L, projection.summary().getErrorLogs()),
                () -> assertEquals(0L, projection.summary().getWarnLogs()),
                () -> assertTrue(projection.summary().getLevelCounts().isEmpty()),
                () -> assertTrue(projection.summary().getTopLoggers().isEmpty()),
                () -> assertTrue(projection.recentLogs().isEmpty()));
    }
}
