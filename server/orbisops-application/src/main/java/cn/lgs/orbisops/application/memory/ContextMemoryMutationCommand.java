package cn.lgs.orbisops.application.memory;

import java.math.BigDecimal;

/** Typed create/update input. Null fields mean absent for partial updates. */
public record ContextMemoryMutationCommand(
        String memoryId,
        String scopeType,
        String scopeId,
        String memoryType,
        String title,
        String summary,
        String content,
        String keywords,
        String status,
        boolean confidencePresent,
        BigDecimal confidence,
        String sourceType,
        String sourceId,
        String sourceMessageHash,
        String createdBy) {
}
