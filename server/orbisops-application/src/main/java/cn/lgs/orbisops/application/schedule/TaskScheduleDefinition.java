package cn.lgs.orbisops.application.schedule;

import java.time.LocalDateTime;

/** Narrow typed Application model for one scheduled Agent task. */
public record TaskScheduleDefinition(
        Long id,
        String projectId,
        String agentId,
        String taskName,
        String description,
        String cronExpression,
        TaskScheduleRuntimeConfiguration runtimeConfiguration,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        String createdBy) {

    public TaskScheduleDefinition(Long id, String projectId, String agentId, String taskName,
            String description, String cronExpression, TaskScheduleRuntimeConfiguration runtimeConfiguration,
            Integer status, LocalDateTime createTime, LocalDateTime updateTime) {
        this(id, projectId, agentId, taskName, description, cronExpression, runtimeConfiguration,
                status, createTime, updateTime, null);
    }

    public TaskScheduleDefinition withStatus(Integer resolvedStatus, LocalDateTime resolvedUpdateTime) {
        return new TaskScheduleDefinition(
                id,
                projectId,
                agentId,
                taskName,
                description,
                cronExpression,
                runtimeConfiguration,
                resolvedStatus,
                createTime,
                resolvedUpdateTime, createdBy);
    }
}
