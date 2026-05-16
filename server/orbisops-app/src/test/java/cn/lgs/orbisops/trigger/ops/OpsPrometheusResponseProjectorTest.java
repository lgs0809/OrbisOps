package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsPrometheusResponseProjectorTest {

    private final OpsPrometheusResponseProjector projector = new OpsPrometheusResponseProjector();

    @Test
    void shouldProjectSummaryEndpointMetricsAndAnomalyDecision() {
        OpsPrometheusQueryProtocolService.Result source = new OpsPrometheusQueryProtocolService.Result(
                2,
                1,
                15D,
                0.3D,
                2D,
                70D,
                30D,
                List.of(new OpsPrometheusQueryProtocolService.EndpointMetric(
                        "/api/orders",
                        "POST",
                        "500",
                        3D,
                        125.5D)));

        OpsPrometheusResponseProjector.Projection projection = projector.project(source);

        assertAll(
                () -> assertEquals(2, projection.summary().getInstanceTotal()),
                () -> assertEquals(1, projection.summary().getInstanceUp()),
                () -> assertEquals(15D, projection.summary().getTotalQps()),
                () -> assertEquals(0.3D, projection.summary().getErrorQps()),
                () -> assertEquals(2D, projection.summary().getErrorRate()),
                () -> assertEquals(1, projection.endpointMetrics().size()),
                () -> assertEquals("/api/orders", projection.endpointMetrics().get(0).getUri()),
                () -> assertEquals("POST", projection.endpointMetrics().get(0).getMethod()),
                () -> assertEquals("500", projection.endpointMetrics().get(0).getStatus()),
                () -> assertEquals(3D, projection.endpointMetrics().get(0).getQps()),
                () -> assertEquals(125.5D, projection.endpointMetrics().get(0).getAvgResponseMs()),
                () -> assertTrue(projection.anomaly()));
    }

    @Test
    void thresholdsMustBeStrictlyGreaterThanConfiguredBoundary() {
        OpsAnalysisResponseDTO.MetricSummaryDTO boundary = metrics(2, 2, 1D, 80D, 80D);

        assertAll(
                () -> assertFalse(projector.hasAnomaly(null)),
                () -> assertFalse(projector.hasAnomaly(boundary)),
                () -> assertTrue(projector.hasAnomaly(metrics(2, 1, 0D, 0D, 0D))),
                () -> assertTrue(projector.hasAnomaly(metrics(2, 2, 1.01D, 0D, 0D))),
                () -> assertTrue(projector.hasAnomaly(metrics(2, 2, 0D, 80.01D, 0D))),
                () -> assertTrue(projector.hasAnomaly(metrics(2, 2, 0D, 0D, 80.01D))));
    }

    @Test
    void nullResultShouldProjectEmptyStableResponse() {
        OpsPrometheusResponseProjector.Projection projection = projector.project(null);

        assertAll(
                () -> assertEquals(0, projection.summary().getInstanceTotal()),
                () -> assertEquals(0, projection.summary().getInstanceUp()),
                () -> assertEquals(0D, projection.summary().getTotalQps()),
                () -> assertEquals(0D, projection.summary().getErrorRate()),
                () -> assertTrue(projection.endpointMetrics().isEmpty()),
                () -> assertFalse(projection.anomaly()));
    }

    private OpsAnalysisResponseDTO.MetricSummaryDTO metrics(
            int total,
            int up,
            double errorRate,
            double heap,
            double cpu) {
        return OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                .instanceTotal(total)
                .instanceUp(up)
                .errorRate(errorRate)
                .heapMemoryUsagePercent(heap)
                .processCpuUsagePercent(cpu)
                .build();
    }
}
