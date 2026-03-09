package cn.lgs.orbisops.infrastructure.adapter.model;

import cn.lgs.orbisops.domain.agent.service.model.AiModelAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiModelAvailabilityAdapterTest {

    @Test
    void marksLocalEmbeddingUnavailableWhenSidecarIsNotReady() {
        AiModelAvailability availability = availability();
        ReflectionTestUtils.setField(availability, "embeddingApiKey", "local-rag-models");
        ReflectionTestUtils.setField(availability, "embeddingBaseUrl", "http://127.0.0.1:1");

        assertFalse(availability.isEmbeddingAvailable());
    }

    @Test
    void doesNotProbeRemoteEmbeddingEndpoint() {
        AiModelAvailability availability = availability();
        ReflectionTestUtils.setField(availability, "embeddingApiKey", "sk-real");
        ReflectionTestUtils.setField(availability, "embeddingBaseUrl", "https://api.example.com");

        assertTrue(availability.isEmbeddingAvailable());
    }

    @Test
    void rejectsPlaceholderKeysAndDisabledModelCalls() {
        AiModelAvailability availability = availability();
        ReflectionTestUtils.setField(availability, "openAiApiKey", "dev-placeholder-key");
        assertFalse(availability.isChatAvailable());
        ReflectionTestUtils.setField(availability, "openAiApiKey", "sk-real");
        ReflectionTestUtils.setField(availability, "modelCallsEnabled", false);
        assertFalse(availability.isChatAvailable());
    }

    private SpringAiModelAvailabilityAdapter availability() {
        SpringAiModelAvailabilityAdapter availability = new SpringAiModelAvailabilityAdapter();
        ReflectionTestUtils.setField(availability, "modelCallsEnabled", true);
        ReflectionTestUtils.setField(availability, "placeholderApiKey", "dev-placeholder-key");
        ReflectionTestUtils.setField(availability, "openAiApiKey", "sk-real");
        ReflectionTestUtils.setField(availability, "embeddingApiKey", "sk-real");
        ReflectionTestUtils.setField(availability, "embeddingBaseUrl", "https://api.example.com");
        ReflectionTestUtils.setField(availability, "rerankApiKey", "sk-real");
        ReflectionTestUtils.setField(availability, "rerankBaseUrl", "https://api.example.com");
        ReflectionTestUtils.setField(availability, "localModelReadinessCacheMs", 1000L);
        ReflectionTestUtils.setField(availability, "localModelReadinessTimeoutMs", 100);
        return availability;
    }
}
