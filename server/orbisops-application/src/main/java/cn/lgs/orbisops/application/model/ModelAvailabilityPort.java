package cn.lgs.orbisops.application.model;

/** Outbound application port for model-runtime availability and readiness facts. */
public interface ModelAvailabilityPort {

    boolean isChatAvailable();

    boolean isEmbeddingAvailable();

    /** Configuration intent is distinct from temporary endpoint readiness. */
    default boolean isEmbeddingConfigured() {return isEmbeddingAvailable();}

    boolean isRerankAvailable(String apiKey);

    boolean isApiKeyUsable(String apiKey);

    void assertChatAvailable(String feature);

    String unavailableMessage(String feature);

    ModelAvailabilitySnapshot snapshot();
}
