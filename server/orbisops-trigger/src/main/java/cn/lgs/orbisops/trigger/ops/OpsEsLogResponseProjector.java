package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Projects the ES protocol result into the public analysis response model. */
final class OpsEsLogResponseProjector {

    Projection project(OpsEsLogQueryProtocolService.Result queryResult) {
        OpsEsLogQueryProtocolService.Result result = queryResult == null
                ? new OpsEsLogQueryProtocolService.Result(0L, Map.of(), List.of(), List.of())
                : queryResult;
        Map<String, Long> levelCounts = result.levelCounts() == null ? Map.of() : result.levelCounts();
        OpsAnalysisResponseDTO.LogSummaryDTO summary = OpsAnalysisResponseDTO.LogSummaryDTO.builder()
                .totalLogs(result.totalLogs())
                .errorLogs(levelCounts.getOrDefault("ERROR", 0L))
                .warnLogs(levelCounts.getOrDefault("WARN", 0L))
                .levelCounts(levelCounts)
                .topLoggers(projectBuckets(result.topLoggers()))
                .build();
        return new Projection(summary, projectLogSamples(result.recentLogs()));
    }

    private List<OpsAnalysisResponseDTO.BucketDTO> projectBuckets(
            List<OpsEsLogQueryProtocolService.Bucket> buckets) {
        List<OpsAnalysisResponseDTO.BucketDTO> result = new ArrayList<>();
        for (OpsEsLogQueryProtocolService.Bucket bucket : buckets == null ? List.<OpsEsLogQueryProtocolService.Bucket>of() : buckets) {
            result.add(OpsAnalysisResponseDTO.BucketDTO.builder()
                    .key(bucket.key())
                    .count(bucket.count())
                    .build());
        }
        return result;
    }

    private List<OpsAnalysisResponseDTO.LogSampleDTO> projectLogSamples(
            List<OpsEsLogQueryProtocolService.LogSample> samples) {
        List<OpsAnalysisResponseDTO.LogSampleDTO> result = new ArrayList<>();
        for (OpsEsLogQueryProtocolService.LogSample sample : samples == null ? List.<OpsEsLogQueryProtocolService.LogSample>of() : samples) {
            result.add(OpsAnalysisResponseDTO.LogSampleDTO.builder()
                    .timestamp(sample.timestamp())
                    .level(sample.level())
                    .loggerName(sample.loggerName())
                    .message(sample.message())
                    .build());
        }
        return result;
    }

    record Projection(
            OpsAnalysisResponseDTO.LogSummaryDTO summary,
            List<OpsAnalysisResponseDTO.LogSampleDTO> recentLogs) {
    }
}
