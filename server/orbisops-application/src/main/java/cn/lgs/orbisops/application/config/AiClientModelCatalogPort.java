package cn.lgs.orbisops.application.config;

import java.util.List;

/** Persistence boundary for the AI model catalog. */
public interface AiClientModelCatalogPort {

    boolean insert(AiClientModelDefinition definition);

    boolean updateById(AiClientModelDefinition definition);

    boolean updateByModelId(AiClientModelDefinition definition);

    boolean deleteById(Long id);

    boolean deleteByModelId(String modelId);

    AiClientModelDefinition findById(Long id);

    AiClientModelDefinition findByModelId(String modelId);

    List<AiClientModelDefinition> findByApiId(String apiId);

    List<AiClientModelDefinition> findByModelType(String modelType);

    List<AiClientModelDefinition> listEnabled();

    List<AiClientModelDefinition> listAll();
}
