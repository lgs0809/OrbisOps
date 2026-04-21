package cn.lgs.orbisops.domain.repair.model;

public record CodeDeliveryPullRequest(String url) {

    public CodeDeliveryPullRequest {
        url = required(url, "CODE_DELIVERY_PULL_REQUEST_URL_REQUIRED");
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
