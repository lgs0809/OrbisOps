package cn.lgs.orbisops.domain.agent.model.valobj;

import java.time.LocalDateTime;

/**
 * Persistence-neutral record of a model provider health probe.
 */
public record AiClientApiHealthCheckRecord(
        String checkId,
        String apiId,
        String testType,
        String endpoint,
        String status,
        Integer httpStatus,
        Long latencyMs,
        String errorMessage,
        String testedBy,
        LocalDateTime testTime,
        LocalDateTime createTime) {
}
