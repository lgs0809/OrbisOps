package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsMySqlSlowSqlQueryFactoryTest {

    private final OpsMySqlSlowSqlQueryFactory factory = new OpsMySqlSlowSqlQueryFactory();

    @Test
    void shouldClampRangeAndTimeoutToLowerBounds() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .rangeMinutes(0)
                .nodeTimeoutSeconds(0)
                .build();
        OpsMySqlSlowSqlSettings settings = new OpsMySqlSlowSqlSettings(true, 7, 250D, false);

        MySqlSlowSqlQuery query = factory.create(request, settings);

        assertAll(
                () -> assertEquals(1, query.rangeMinutes()),
                () -> assertEquals(1, query.queryTimeoutSeconds()),
                () -> assertEquals(250D, query.thresholdMs()),
                () -> assertEquals(7, query.sampleSize()),
                () -> assertFalse(query.performanceSchemaFallback()));
    }

    @Test
    void shouldClampRangeAndTimeoutToUpperBounds() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .rangeMinutes(5000)
                .nodeTimeoutSeconds(5000)
                .build();
        OpsMySqlSlowSqlSettings settings = new OpsMySqlSlowSqlSettings(true, 50, 1000D, true);

        MySqlSlowSqlQuery query = factory.create(request, settings);

        assertAll(
                () -> assertEquals(1440, query.rangeMinutes()),
                () -> assertEquals(300, query.queryTimeoutSeconds()),
                () -> assertEquals(1000D, query.thresholdMs()),
                () -> assertEquals(50, query.sampleSize()),
                () -> assertEquals(true, query.performanceSchemaFallback()));
    }

    @Test
    void nullInputShouldUseStableDefaults() {
        MySqlSlowSqlQuery query = factory.create(null, null);

        assertAll(
                () -> assertEquals(15, query.rangeMinutes()),
                () -> assertEquals(30, query.queryTimeoutSeconds()),
                () -> assertEquals(500D, query.thresholdMs()),
                () -> assertEquals(10, query.sampleSize()),
                () -> assertEquals(true, query.performanceSchemaFallback()));
    }
}
