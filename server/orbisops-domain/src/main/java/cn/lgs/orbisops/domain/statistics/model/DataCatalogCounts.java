package cn.lgs.orbisops.domain.statistics.model;

public record DataCatalogCounts(
        long activeAgentCount,
        long mcpToolCount,
        long ragOrderCount,
        long modelCount) {

    public DataCatalogCounts {
        activeAgentCount = Math.max(0L, activeAgentCount);
        mcpToolCount = Math.max(0L, mcpToolCount);
        ragOrderCount = Math.max(0L, ragOrderCount);
        modelCount = Math.max(0L, modelCount);
    }
}
