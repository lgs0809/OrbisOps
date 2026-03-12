package cn.lgs.orbisops.application.config;

/** Typed filtering and pagination request for the API provider catalog. */
public record AiClientApiCatalogQuery(
        String apiId,
        String baseUrl,
        Integer status,
        int pageNum,
        int pageSize) {

    public AiClientApiCatalogQuery {
        pageNum = Math.max(1, pageNum);
        pageSize = Math.max(1, pageSize);
    }

    public static AiClientApiCatalogQuery all() {
        return new AiClientApiCatalogQuery(null, null, null, 1, 10);
    }
}
