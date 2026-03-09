package cn.lgs.orbisops.trigger.application.modelpolicy;

import cn.lgs.orbisops.api.dto.AiClientModelResponseDTO;
import cn.lgs.orbisops.application.modelpolicy.ModelCatalogPort;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelUsage;
import cn.lgs.orbisops.trigger.application.config.AiClientModelApplicationService;
import org.springframework.stereotype.Component;

@Component
public class OpsModelCatalogAdapter implements ModelCatalogPort {

    private final AiClientModelApplicationService models;

    public OpsModelCatalogAdapter(AiClientModelApplicationService models) {
        this.models = models;
    }

    @Override
    public void requireAvailable(String modelId, ModelUsage usage) {
        AiClientModelResponseDTO model = models.queryByModelId(modelId);
        if (model == null || model.getStatus() == null || model.getStatus() != 1) {
            throw new IllegalArgumentException("MODEL_POLICY_MODEL_UNAVAILABLE:" + modelId);
        }
        String actualUsage = value(model.getModelUsage()).isBlank()
                ? value(model.getModelType())
                : value(model.getModelUsage());
        if (!actualUsage.toUpperCase().contains(usage.name())) {
            throw new IllegalArgumentException("MODEL_POLICY_USAGE_MISMATCH:"
                    + modelId + ":" + usage.name());
        }
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }
}
