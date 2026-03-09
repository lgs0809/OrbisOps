package cn.lgs.orbisops.domain.repair.model;

public record CodeDeliveryBranch(String branchName, String commitSha) {

    public CodeDeliveryBranch {
        branchName = required(branchName, "CODE_DELIVERY_BRANCH_REQUIRED");
        commitSha = required(commitSha, "CODE_DELIVERY_COMMIT_REQUIRED").toLowerCase();
        if (!commitSha.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("CODE_DELIVERY_COMMIT_INVALID");
        }
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
