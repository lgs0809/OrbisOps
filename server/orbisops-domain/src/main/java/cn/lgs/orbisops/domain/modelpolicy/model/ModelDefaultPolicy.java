package cn.lgs.orbisops.domain.modelpolicy.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ModelDefaultPolicy(String projectId,
                                 String chatModelId,
                                 String embeddingModelId,
                                 String rerankModelId,
                                 String visionModelId,
                                 ModelPolicyStatus status) {

    public ModelDefaultPolicy {
        projectId = value(projectId);
        chatModelId = modelId(chatModelId);
        embeddingModelId = modelId(embeddingModelId);
        rerankModelId = modelId(rerankModelId);
        visionModelId = modelId(visionModelId);
        if (status == null) throw new IllegalArgumentException("MODEL_POLICY_STATUS_REQUIRED");
    }

    public Map<ModelUsage, String> modelReferences() {
        Map<ModelUsage, String> result = new LinkedHashMap<>();
        result.put(ModelUsage.CHAT, chatModelId);
        result.put(ModelUsage.EMBED, embeddingModelId);
        result.put(ModelUsage.RERANK, rerankModelId);
        result.put(ModelUsage.VISION, visionModelId);
        return Map.copyOf(result);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("scope", projectId.isBlank() ? "GLOBAL" : "PROJECT");
        result.put("defaultChatModelId", chatModelId);
        result.put("defaultEmbeddingModelId", embeddingModelId);
        result.put("defaultRerankModelId", rerankModelId);
        result.put("defaultVisionModelId", visionModelId);
        result.put("status", status.name());
        return result;
    }

    private static String modelId(String input) {
        String value = value(input);
        if (value.length() > 128) throw new IllegalArgumentException("MODEL_POLICY_MODEL_ID_TOO_LONG");
        return value;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
