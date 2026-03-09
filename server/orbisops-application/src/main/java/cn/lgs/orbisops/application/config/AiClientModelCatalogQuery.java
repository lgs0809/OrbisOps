package cn.lgs.orbisops.application.config;

/** Ordered legacy-compatible query selector for the AI model catalog. */
public record AiClientModelCatalogQuery(
        String modelId,
        String apiId,
        String modelType,
        Integer status) {

    public static AiClientModelCatalogQuery all() {
        return new AiClientModelCatalogQuery(null, null, null, null);
    }
}
