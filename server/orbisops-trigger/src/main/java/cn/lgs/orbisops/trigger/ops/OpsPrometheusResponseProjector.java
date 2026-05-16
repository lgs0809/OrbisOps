package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Projects Prometheus protocol results and owns metric anomaly thresholds. */
final class OpsPrometheusResponseProjector {

    Projection project(OpsPrometheusQueryProtocolService.Result result) {
        OpsPrometheusQueryProtocolService.Result source = result == null
                ? new OpsPrometheusQueryProtocolService.Result(0, 0, 0D, 0D, 0D, 0D, 0D, List.of())
                : result;
        OpsAnalysisResponseDTO.MetricSummaryDTO summary = OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                .instanceTotal(source.instanceTotal())
                .instanceUp(source.instanceUp())
                .totalQps(source.totalQps())
                .errorQps(source.errorQps())
                .errorRate(source.errorRate())
                .heapMemoryUsagePercent(source.heapMemoryUsagePercent())
                .processCpuUsagePercent(source.processCpuUsagePercent())
                .build();
        return new Projection(summary, projectEndpointMetrics(source.endpointMetrics()), hasAnomaly(summary));
    }

    private List<OpsAnalysisResponseDTO.EndpointMetricDTO> projectEndpointMetrics(
            List<OpsPrometheusQueryProtocolService.EndpointMetric> metrics) {
        List<OpsAnalysisResponseDTO.EndpointMetricDTO> result = new ArrayList<>();
        for (OpsPrometheusQueryProtocolService.EndpointMetric metric : metrics == null
                ? List.<OpsPrometheusQueryProtocolService.EndpointMetric>of()
                : metrics) {
            result.add(OpsAnalysisResponseDTO.EndpointMetricDTO.builder()
                    .uri(metric.uri())
                    .method(metric.method())
                    .status(metric.status())
                    .qps(metric.qps())
                    .avgResponseMs(metric.avgResponseMs())
                    .build());
        }
        return result;
    }

    boolean hasAnomaly(OpsAnalysisResponseDTO.MetricSummaryDTO metrics) {
        if (metrics == null) {
            return false;
        }
        return (metrics.getInstanceTotal() != null
                && metrics.getInstanceTotal() > 0
                && !Objects.equals(metrics.getInstanceTotal(), metrics.getInstanceUp()))
                || (metrics.getErrorRate() != null && metrics.getErrorRate() > 1D)
                || (metrics.getHeapMemoryUsagePercent() != null && metrics.getHeapMemoryUsagePercent() > 80D)
                || (metrics.getProcessCpuUsagePercent() != null && metrics.getProcessCpuUsagePercent() > 80D);
    }

    record Projection(
            OpsAnalysisResponseDTO.MetricSummaryDTO summary,
            List<OpsAnalysisResponseDTO.EndpointMetricDTO> endpointMetrics,
            boolean anomaly) {
    }
}
