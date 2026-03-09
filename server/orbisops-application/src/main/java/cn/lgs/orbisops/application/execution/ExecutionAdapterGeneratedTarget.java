package cn.lgs.orbisops.application.execution;

import java.util.List;

public record ExecutionAdapterGeneratedTarget(
        String projectId,
        String executionTargetId,
        String targetName,
        String adapterTemplateId,
        String adapterType,
        String workerId,
        List<String> environments,
        String status,
        String updatedAt
) {

    public ExecutionAdapterGeneratedTarget {
        projectId = text(projectId);
        executionTargetId = text(executionTargetId);
        targetName = text(targetName);
        adapterTemplateId = text(adapterTemplateId);
        adapterType = text(adapterType);
        workerId = text(workerId);
        environments = environments == null ? List.of() : List.copyOf(environments);
        status = text(status);
        updatedAt = text(updatedAt);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
