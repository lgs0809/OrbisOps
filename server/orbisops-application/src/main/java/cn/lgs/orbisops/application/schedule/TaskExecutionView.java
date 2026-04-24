package cn.lgs.orbisops.application.schedule;

import java.time.LocalDateTime;

/** Typed Application projection for one scheduled task execution. */
public record TaskExecutionView(
        Long id,
        Long scheduleId,
        String taskName,
        String agentId,
        String triggerType,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        String input,
        String output,
        String errorMessage) {
}
