package cn.lgs.orbisops.domain.modelpolicy.service;

import cn.lgs.orbisops.domain.modelpolicy.model.ModelDefaultPolicy;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelPolicyStatus;

import java.util.Map;

public final class ModelDefaultPolicyFactory {

    public ModelDefaultPolicy create(String projectId, Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new ModelDefaultPolicy(
                value(projectId),
                text(safe.get("defaultChatModelId")),
                text(safe.get("defaultEmbeddingModelId")),
                text(safe.get("defaultRerankModelId")),
                text(safe.get("defaultVisionModelId")),
                ModelPolicyStatus.require(text(safe.get("status"))));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
