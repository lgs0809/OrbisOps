package cn.lgs.orbisops.domain.repair.model;

public record RepairWorkspaceCandidate(
        String projectId,
        String serviceId,
        String environment,
        String summary,
        String unifiedDiff,
        String baseCommit) {
}
