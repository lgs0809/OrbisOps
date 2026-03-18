package cn.lgs.orbisops.domain.source.model;

import java.util.List;

public record ProjectServiceCandidate(
        String projectId,
        String serviceId,
        String name,
        String repositoryId,
        String modulePath,
        String buildProfile,
        String artifactPath,
        String deploymentResourceId,
        String healthUrl,
        List<String> smokeUrls) {
}
