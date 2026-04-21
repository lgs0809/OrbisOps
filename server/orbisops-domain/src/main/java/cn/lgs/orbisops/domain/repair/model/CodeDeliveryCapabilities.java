package cn.lgs.orbisops.domain.repair.model;

public record CodeDeliveryCapabilities(
        boolean localBranch,
        boolean githubPullRequest,
        boolean githubActionsStatus,
        boolean arbitraryRemote) {
}
