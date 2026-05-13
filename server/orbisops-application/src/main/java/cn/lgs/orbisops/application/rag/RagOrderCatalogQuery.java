package cn.lgs.orbisops.application.rag;

/** Typed query specification for the legacy RAG order catalog. */
public record RagOrderCatalogQuery(
        String ragId,
        String ragName,
        String knowledgeTag,
        Integer status,
        Integer pageNum,
        Integer pageSize) {

    public static RagOrderCatalogQuery all() {
        return new RagOrderCatalogQuery(null, null, null, null, null, null);
    }

    public boolean paged() {
        return pageNum != null && pageSize != null;
    }

    public int normalizedPageNum() {
        return Math.max(1, pageNum == null ? 1 : pageNum);
    }

    public int normalizedPageSize() {
        return Math.max(1, pageSize == null ? 1 : pageSize);
    }
}
