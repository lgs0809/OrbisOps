package cn.lgs.orbisops.application.statistics;

import cn.lgs.orbisops.domain.statistics.adapter.repository.IDataStatisticsReadRepository;
import cn.lgs.orbisops.domain.statistics.model.DataCatalogCounts;
import cn.lgs.orbisops.domain.statistics.model.DataStatisticsSnapshot;
import cn.lgs.orbisops.domain.statistics.model.ExecutionStatisticsFacts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataStatisticsQueryApplicationServiceTest {

    @Test
    void snapshotCombinesCatalogAndExecutionReadModels() {
        DataCatalogStatisticsPort catalog = () -> new DataCatalogCounts(7, 8, 9, 10);
        IDataStatisticsReadRepository execution = () -> new ExecutionStatisticsFacts(
                11, 12, 13,
                14, 15,
                4, 3,
                20, 1,
                30, 1);
        DataStatisticsQueryApplicationService service =
                new DataStatisticsQueryApplicationService(catalog, execution);

        DataStatisticsSnapshot snapshot = service.snapshot();

        assertEquals(7, snapshot.activeAgentCount());
        assertEquals(8, snapshot.mcpToolCount());
        assertEquals(9, snapshot.ragOrderCount());
        assertEquals(10, snapshot.modelCount());
        assertEquals(36, snapshot.todayRequestCount());
        assertEquals(75D, snapshot.successRate());
        assertEquals(29, snapshot.runningTaskCount());
    }

    @Test
    void missingCatalogSnapshotFailsClosed() {
        DataStatisticsQueryApplicationService service = new DataStatisticsQueryApplicationService(
                () -> null,
                () -> new ExecutionStatisticsFacts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        assertThrows(IllegalStateException.class, service::snapshot);
    }

    @Test
    void requiredPortsAreValidated() {
        IDataStatisticsReadRepository execution =
                () -> new ExecutionStatisticsFacts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new DataStatisticsQueryApplicationService(null, execution));
        assertThrows(IllegalArgumentException.class,
                () -> new DataStatisticsQueryApplicationService(
                        () -> new DataCatalogCounts(0, 0, 0, 0), null));
    }
}
