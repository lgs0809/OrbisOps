package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagVisualAnalysisAvailabilityTest {

    @Test
    void enabledConfigurationMustStillRespectHighValueGate() {
        RagVisualAnalysisAvailability availability = new RagVisualAnalysisAvailability(
                settings(true, true, "openai", "https://api.example.com", "test-api-key"),
                null);

        assertTrue(availability.shouldAnalyze(Map.of("high_value_candidate", true)));
        assertFalse(availability.shouldAnalyze(Map.of("high_value_candidate", false)));
    }

    @Test
    void disabledUnsupportedOrIncompleteConfigurationMustBeUnavailable() {
        assertFalse(new RagVisualAnalysisAvailability(
                settings(false, false, "openai", "https://api.example.com", "key"), null)
                .shouldAnalyze(Map.of()));
        assertFalse(new RagVisualAnalysisAvailability(
                settings(true, false, "other", "https://api.example.com", "key"), null)
                .shouldAnalyze(Map.of()));
        assertFalse(new RagVisualAnalysisAvailability(
                settings(true, false, "openai", "", "key"), null)
                .shouldAnalyze(Map.of()));
        assertFalse(new RagVisualAnalysisAvailability(
                settings(true, false, "openai", "https://api.example.com", ""), null)
                .shouldAnalyze(Map.of()));
    }

    @Test
    void modelAvailabilityPortMustGateConfiguredAnalyzer() {
        ModelAvailabilityPort models = mock(ModelAvailabilityPort.class);
        when(models.isRerankAvailable("test-api-key")).thenReturn(false, true);
        RagVisualAnalysisAvailability availability = new RagVisualAnalysisAvailability(
                settings(true, false, "openai", "https://api.example.com", "test-api-key"),
                models);

        assertFalse(availability.shouldAnalyze(Map.of()));
        assertTrue(availability.shouldAnalyze(Map.of()));
        verify(models, org.mockito.Mockito.times(2)).isRerankAvailable("test-api-key");
    }

    private RagVisualAnalysisSettings settings(
            boolean enabled,
            boolean highValueOnly,
            String provider,
            String baseUrl,
            String apiKey) {
        return new RagVisualAnalysisSettings(
                enabled, highValueOnly, provider, baseUrl, apiKey,
                "v1/chat/completions", "model", "low", 30, 1200,
                "max_completion_tokens", "json_schema", 1, 3,
                4_194_304L, 144);
    }
}
