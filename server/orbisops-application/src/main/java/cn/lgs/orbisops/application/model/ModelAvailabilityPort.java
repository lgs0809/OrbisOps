package cn.lgs.orbisops.application.model;

/** Outbound application port for model-runtime availability and readiness facts. */
public interface ModelAvailabilityPort {

    boolean isChatAvailable();

    boolean isEmbeddingAvailable();

    boolean isRerankAvailable(String apiKey);

    boolean isApiKeyUsable(String apiKey);

    void assertChatAvailable(String feature);

    String unavailableMessage(String feature);

    ModelAvailabilitySnapshot snapshot();
}
