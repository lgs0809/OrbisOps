package cn.lgs.orbisops.domain.source.model;

public record DeploymentRevisionCandidate(
        String projectId,
        String repositoryId,
        String environment,
        String serviceName,
        String revision,
        String imageRef) {
}
