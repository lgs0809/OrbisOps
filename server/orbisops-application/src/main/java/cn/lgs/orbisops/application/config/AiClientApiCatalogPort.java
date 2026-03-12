package cn.lgs.orbisops.application.config;

import java.util.List;

/** Persistence port for the typed AI model-provider API catalog. */
public interface AiClientApiCatalogPort {

    boolean insert(AiClientApiDefinition definition);

    boolean updateById(AiClientApiDefinition definition);

    boolean updateByApiId(AiClientApiDefinition definition);

    boolean deleteById(Long id);

    boolean deleteByApiId(String apiId);

    AiClientApiDefinition findById(Long id);

    AiClientApiDefinition findByApiId(String apiId);

    List<AiClientApiDefinition> listEnabled();

    List<AiClientApiDefinition> listAll();
}
