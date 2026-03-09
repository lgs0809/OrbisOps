package cn.lgs.orbisops.application.statistics;

import cn.lgs.orbisops.domain.statistics.adapter.repository.IDataStatisticsReadRepository;
import cn.lgs.orbisops.domain.statistics.model.DataCatalogCounts;
import cn.lgs.orbisops.domain.statistics.model.DataStatisticsSnapshot;
import cn.lgs.orbisops.domain.statistics.model.ExecutionStatisticsSummary;
import cn.lgs.orbisops.domain.statistics.service.DataStatisticsPolicy;

public final class DataStatisticsQueryApplicationService {

    private final DataCatalogStatisticsPort catalog;
    private final IDataStatisticsReadRepository executionStatistics;
    private final DataStatisticsPolicy policy;

    public DataStatisticsQueryApplicationService(
            DataCatalogStatisticsPort catalog,
            IDataStatisticsReadRepository executionStatistics) {
        if (catalog == null) throw new IllegalArgumentException("DATA_CATALOG_STATISTICS_PORT_REQUIRED");
        if (executionStatistics == null) {
            throw new IllegalArgumentException("DATA_STATISTICS_READ_REPOSITORY_REQUIRED");
        }
        this.catalog = catalog;
        this.executionStatistics = executionStatistics;
        this.policy = new DataStatisticsPolicy();
    }

    public DataStatisticsSnapshot snapshot() {
        DataCatalogCounts counts = catalog.loadCatalogCounts();
        if (counts == null) throw new IllegalStateException("DATA_CATALOG_STATISTICS_UNAVAILABLE");
        ExecutionStatisticsSummary execution = policy.summarize(
                executionStatistics.loadExecutionFacts());
        return new DataStatisticsSnapshot(
                counts.activeAgentCount(),
                counts.mcpToolCount(),
                counts.ragOrderCount(),
                counts.modelCount(),
                execution.todayRequestCount(),
                execution.successRate(),
                execution.runningTaskCount());
    }
}
