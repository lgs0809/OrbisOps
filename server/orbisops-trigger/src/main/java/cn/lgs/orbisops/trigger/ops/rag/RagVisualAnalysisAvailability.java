package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;

import java.util.Map;

/** Decides whether visual analysis is available for a document. */
public final class RagVisualAnalysisAvailability {

    private final RagVisualAnalysisSettings settings;
    private final ModelAvailabilityPort aiModelAvailability;

    public RagVisualAnalysisAvailability(
            RagVisualAnalysisSettings settings,
            ModelAvailabilityPort aiModelAvailability) {
        if (settings == null) throw new IllegalArgumentException("RAG_VISUAL_SETTINGS_REQUIRED");
        this.settings = settings;
        this.aiModelAvailability = aiModelAvailability;
    }

    public boolean shouldAnalyze(Map<String, Object> metadata) {
        if (!settings.enabled()
                || !settings.providerSupported()
                || settings.baseUrl().isBlank()
                || settings.apiKey().isBlank()) {
            return false;
        }
        if (aiModelAvailability != null
                && !aiModelAvailability.isRerankAvailable(settings.apiKey())) {
            return false;
        }
        return !settings.highValueOnly()
                || Boolean.TRUE.equals(metadata.get("high_value_candidate"));
    }
}
