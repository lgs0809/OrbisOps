package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JdbcCodeDeliverySchemaInitializerTest {

    @Test
    void infrastructureOwnsDeliveryDdl() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcCodeDeliverySchemaInitializer initializer =
                new JdbcCodeDeliverySchemaInitializer(jdbc, true, true);

        initializer.initialize();

        verify(jdbc).execute(contains("CREATE TABLE IF NOT EXISTS ai_ops_code_delivery"));
    }

    @Test
    void disabledOrManualModeSkipsDdl() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new JdbcCodeDeliverySchemaInitializer(jdbc, false, true).initialize();
        new JdbcCodeDeliverySchemaInitializer(jdbc, true, false).initialize();

        verify(jdbc, never()).execute(anyString());
    }
}
