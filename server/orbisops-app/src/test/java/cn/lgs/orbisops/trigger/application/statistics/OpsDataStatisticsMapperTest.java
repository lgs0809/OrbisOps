package cn.lgs.orbisops.trigger.application.statistics;

import cn.lgs.orbisops.api.dto.DataStatisticsResponseDTO;
import cn.lgs.orbisops.domain.statistics.model.DataStatisticsSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsDataStatisticsMapperTest {

    private final OpsDataStatisticsMapper mapper = new OpsDataStatisticsMapper();

    @Test
    void mapsTypedSnapshotToExistingHttpContract() {
        DataStatisticsResponseDTO response = mapper.response(
                new DataStatisticsSnapshot(1, 2, 3, 4, 5, 66.67D, 6));

        assertEquals(1L, response.getActiveAgentCount());
        assertEquals(2L, response.getMcpToolCount());
        assertEquals(3L, response.getRagOrderCount());
        assertEquals(4L, response.getModelCount());
        assertEquals(5L, response.getTodayRequestCount());
        assertEquals(66.67D, response.getSuccessRate());
        assertEquals(6L, response.getRunningTaskCount());
    }

    @Test
    void nullSnapshotIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> mapper.response(null));
    }
}
