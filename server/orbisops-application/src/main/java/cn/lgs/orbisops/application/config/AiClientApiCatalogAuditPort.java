package cn.lgs.orbisops.application.config;

/** Audit port for successful API provider catalog mutations. */
public interface AiClientApiCatalogAuditPort {

    void created(AiClientApiDefinition after);

    void updatedById(Long id, AiClientApiDefinition before, AiClientApiDefinition after);

    void updatedByApiId(String apiId, AiClientApiDefinition before, AiClientApiDefinition after);

    void deletedById(Long id, AiClientApiDefinition before);

    void deletedByApiId(String apiId, AiClientApiDefinition before);
}
