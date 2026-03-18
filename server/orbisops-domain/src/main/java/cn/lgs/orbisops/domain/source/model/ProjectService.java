package cn.lgs.orbisops.domain.source.model;

import java.util.List;

public record ProjectService(
        String serviceId,
        String projectId,
        String name,
        String repositoryId,
        String modulePath,
        BuildProfile buildProfile,
        String artifactPath,
        String deploymentResourceId,
        String healthUrl,
        List<String> smokeUrls,
        String status,
        String createdAt,
        String updatedAt) {

    public ProjectService {
        serviceId = required(serviceId, "SOURCE_SERVICE_ID_REQUIRED");
        projectId = required(projectId, "SOURCE_PROJECT_ID_REQUIRED");
        name = required(name, "SOURCE_SERVICE_NAME_REQUIRED");
        repositoryId = required(repositoryId, "SOURCE_REPOSITORY_ID_REQUIRED");
        modulePath = required(modulePath, "SOURCE_MODULE_PATH_REQUIRED");
        if (buildProfile == null) throw new IllegalArgumentException("SOURCE_BUILD_PROFILE_REQUIRED");
        artifactPath = value(artifactPath);
        deploymentResourceId = value(deploymentResourceId);
        healthUrl = value(healthUrl);
        smokeUrls = smokeUrls == null ? List.of() : smokeUrls.stream()
                .map(ProjectService::value)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        status = required(status, "SOURCE_SERVICE_STATUS_REQUIRED").toUpperCase();
        createdAt = required(createdAt, "SOURCE_SERVICE_CREATED_AT_REQUIRED");
        updatedAt = required(updatedAt, "SOURCE_SERVICE_UPDATED_AT_REQUIRED");
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
