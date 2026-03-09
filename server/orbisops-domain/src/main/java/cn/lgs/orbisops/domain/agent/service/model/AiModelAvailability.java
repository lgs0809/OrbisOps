package cn.lgs.orbisops.domain.agent.service.model;

import java.util.Map;

/** Domain-facing model availability port. Configuration and endpoint probing belong to outer adapters. */
public interface AiModelAvailability {

    boolean isChatAvailable();

    boolean isEmbeddingAvailable();

    boolean isRerankAvailable(String apiKey);

    boolean isApiKeyUsable(String apiKey);

    void assertChatAvailable(String feature);

    String unavailableMessage(String feature);

    Map<String, Object> status();
}
