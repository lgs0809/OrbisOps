package cn.lgs.orbisops.domain.repair.model;

public record CodeDelivery(
        String deliveryId,
        String workspaceId,
        String projectId,
        String serviceId,
        CodeDeliveryMode mode,
        String branchName,
        String commitSha,
        String pullRequestUrl,
        String ciStatus,
        String ciUrl,
        String createdBy,
        String createdAt,
        String updatedAt) {

    public CodeDelivery {
        deliveryId = required(deliveryId, "CODE_DELIVERY_ID_REQUIRED");
        workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
        projectId = required(projectId, "REPAIR_PROJECT_ID_REQUIRED");
        serviceId = required(serviceId, "REPAIR_SERVICE_ID_REQUIRED");
        if (mode == null) throw new IllegalArgumentException("CODE_DELIVERY_MODE_REQUIRED");
        branchName = required(branchName, "CODE_DELIVERY_BRANCH_REQUIRED");
        commitSha = commit(commitSha);
        pullRequestUrl = value(pullRequestUrl);
        ciStatus = required(ciStatus, "CODE_DELIVERY_CI_STATUS_REQUIRED").toUpperCase();
        ciUrl = value(ciUrl);
        createdBy = required(createdBy, "REPAIR_ACTOR_REQUIRED");
        createdAt = required(createdAt, "CODE_DELIVERY_CREATED_AT_REQUIRED");
        updatedAt = required(updatedAt, "CODE_DELIVERY_UPDATED_AT_REQUIRED");
    }

    public CodeDelivery refreshed(String status, String url, String updatedAt) {
        return new CodeDelivery(
                deliveryId, workspaceId, projectId, serviceId, mode, branchName, commitSha,
                pullRequestUrl, status, url, createdBy, createdAt, updatedAt);
    }

    private static String commit(String value) {
        String normalized = required(value, "CODE_DELIVERY_COMMIT_REQUIRED").toLowerCase();
        if (!normalized.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("CODE_DELIVERY_COMMIT_INVALID");
        }
        return normalized;
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
