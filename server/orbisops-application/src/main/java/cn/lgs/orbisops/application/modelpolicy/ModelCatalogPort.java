package cn.lgs.orbisops.application.modelpolicy;

import cn.lgs.orbisops.domain.modelpolicy.model.ModelUsage;

public interface ModelCatalogPort {
    void requireAvailable(String modelId, ModelUsage usage);
}
