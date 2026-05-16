package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQueryResult;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlSample;

import java.util.List;

/** MySQL slow SQL application result to Trigger response projection boundary. */
final class OpsMySqlSlowSqlResponseProjector {

    Projection project(MySqlSlowSqlQueryResult result) {
        List<OpsAnalysisResponseDTO.SlowSqlSampleDTO> samples = result.samples().stream()
                .map(this::projectSample)
                .toList();
        return new Projection(
                result.available(),
                result.sourceName(),
                result.message(),
                result.queryDescription(),
                samples,
                summarize(samples));
    }

    private OpsAnalysisResponseDTO.SlowSqlSampleDTO projectSample(
            MySqlSlowSqlSample sample) {
        return OpsAnalysisResponseDTO.SlowSqlSampleDTO.builder()
                .startTime(sample.startTime())
                .databaseName(sample.databaseName())
                .userHost(sample.userHost())
                .digest(sample.digest())
                .sqlText(sample.sqlText())
                .queryTimeMs(sample.queryTimeMs())
                .rowsExamined(sample.rowsExamined())
                .rowsSent(sample.rowsSent())
                .countStar(sample.countStar())
                .build();
    }

    private OpsAnalysisResponseDTO.SlowSqlSummaryDTO summarize(
            List<OpsAnalysisResponseDTO.SlowSqlSampleDTO> samples) {
        double max = samples.stream()
                .map(OpsAnalysisResponseDTO.SlowSqlSampleDTO::getQueryTimeMs)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0D);
        double average = samples.stream()
                .map(OpsAnalysisResponseDTO.SlowSqlSampleDTO::getQueryTimeMs)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);
        long rowsExamined = samples.stream()
                .map(OpsAnalysisResponseDTO.SlowSqlSampleDTO::getRowsExamined)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .sum();
        return OpsAnalysisResponseDTO.SlowSqlSummaryDTO.builder()
                .totalStatements((long) samples.size())
                .slowStatements((long) samples.size())
                .avgQueryTimeMs(round(average, 2))
                .maxQueryTimeMs(round(max, 2))
                .rowsExamined(rowsExamined)
                .build();
    }

    private double round(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    record Projection(
            boolean available,
            String sourceName,
            String message,
            String queryDescription,
            List<OpsAnalysisResponseDTO.SlowSqlSampleDTO> samples,
            OpsAnalysisResponseDTO.SlowSqlSummaryDTO summary) {
    }
}
