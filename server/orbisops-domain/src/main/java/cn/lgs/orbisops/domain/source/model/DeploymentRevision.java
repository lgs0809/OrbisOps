package cn.lgs.orbisops.domain.source.model;

public record DeploymentRevision(
        String deploymentId,
        String projectId,
        String repositoryId,
        String environment,
        String serviceName,
        String commitSha,
        String imageRef,
        String recordedBy,
        String deployedAt,
        String updatedAt) {

    public DeploymentRevision {
        deploymentId = required(deploymentId, "SOURCE_DEPLOYMENT_ID_REQUIRED");
        projectId = required(projectId, "SOURCE_PROJECT_ID_REQUIRED");
        repositoryId = required(repositoryId, "SOURCE_REPOSITORY_ID_REQUIRED");
        environment = required(environment, "SOURCE_ENVIRONMENT_REQUIRED").toLowerCase();
        serviceName = required(serviceName, "SOURCE_SERVICE_NAME_REQUIRED");
        commitSha = required(commitSha, "SOURCE_DEPLOYMENT_COMMIT_REQUIRED").toLowerCase();
        imageRef = value(imageRef);
        recordedBy = required(recordedBy, "SOURCE_DEPLOYMENT_ACTOR_REQUIRED");
        deployedAt = required(deployedAt, "SOURCE_DEPLOYMENT_TIME_REQUIRED");
        updatedAt = required(updatedAt, "SOURCE_DEPLOYMENT_UPDATED_AT_REQUIRED");
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
