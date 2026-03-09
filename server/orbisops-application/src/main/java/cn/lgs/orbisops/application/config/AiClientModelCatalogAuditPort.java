package cn.lgs.orbisops.application.config;

/** Audit boundary for AI model catalog mutations. */
public interface AiClientModelCatalogAuditPort {

    void created(AiClientModelDefinition created);

    void updatedById(Long id, AiClientModelDefinition before, AiClientModelDefinition after);

    void updatedByModelId(String modelId, AiClientModelDefinition before, AiClientModelDefinition after);

    void deletedById(Long id, AiClientModelDefinition before);

    void deletedByModelId(String modelId, AiClientModelDefinition before);
}
