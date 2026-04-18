package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic records in disposable MySQL; verifies the production observation query. */
@Testcontainers(disabledWithoutDocker = true)
class LandingObservationTimeMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("landing_observation").withUsername("fixture").withPassword("fixture");
    JdbcTemplate jdbc;
    String key;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("jdbc", jdbc);
        var schema = new JdbcChangePackageSchemaInitializer(beans.getBeanProvider(JdbcTemplate.class));
        ReflectionTestUtils.setField(schema, "autoInit", true); schema.initialize();
        new JdbcToolExecutionIdempotencyAdapter(beans.getBeanProvider(JdbcTemplate.class), true).initialize();
        key = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO ai_ops_change_package_landing_operation_run
                (operation_run_id,landing_run_id,package_id,project_id,approved_version,approved_package_hash,
                 execution_key,status,fact_status,result_id,output_hash,started_at,finished_at)
                VALUES (?, 'landing', ?, 'p', 3, 'approval', ?, 'SUCCEEDED', 'COMPLETED', 'receipt', 'hash',
                        NULL, '2026-09-16 03:16:19')
                """,key,key,key);
        jdbc.update("""
                INSERT INTO ai_ops_tool_execution_ledger
                (idempotency_key,project_id,run_id,input_hash,target_hash,side_effecting,status,result_id,output_hash,
                 created_at,updated_at)
                VALUES (?, 'p', 'landing', 'input', 'target', 1, 'SUCCEEDED', 'receipt', 'hash',
                        '2026-09-16 03:16:16.474673','2026-09-16 03:16:19.018007')
                """, key);
    }
    Map<String,Object> observation() {
        return jdbc.queryForList(JdbcChangePackageQueryAdapter.LANDING_OPERATION_QUERY,key,200).get(0);
    }
    @Test void missingLegacyStartUsesOnlyMatchingDurableExecutionAndDoesNotRewriteHistory() {
        var row=observation();
        assertEquals(1789528576.474673,((Number)row.get("startedEpoch")).doubleValue(),0.000001);
        assertEquals("TOOL_EXECUTION_LEDGER_CREATED_AT",row.get("startedEpochSource"));
        assertEquals(1789528579L,((Number)row.get("finishedEpoch")).longValue());
        assertEquals(1789528579.018007,((Number)row.get("finishedEpoch")).doubleValue(),0.000001);
        assertEquals("TOOL_EXECUTION_LEDGER_COMPLETED_AT",row.get("finishedEpochSource"));
        assertNull(jdbc.queryForObject("SELECT started_at FROM ai_ops_change_package_landing_operation_run WHERE execution_key=?",java.sql.Timestamp.class,key));
    }
    @Test void subsecondWriteCannotFinishBeforeItStartsWhenLegacyJournalDropsPrecision() {
        jdbc.update("UPDATE ai_ops_tool_execution_ledger SET created_at='2026-09-16 03:16:19.393506', updated_at='2026-09-16 03:16:19.659678' WHERE idempotency_key=?",key);
        var row=observation();
        assertEquals(0.266172,((Number)row.get("finishedEpoch")).doubleValue()
                -((Number)row.get("startedEpoch")).doubleValue(),0.000002);
        assertEquals("2026-09-16 03:16:19.0",jdbc.queryForObject(
                "SELECT finished_at FROM ai_ops_change_package_landing_operation_run WHERE execution_key=?",java.sql.Timestamp.class,key).toString());
    }
    @ParameterizedTest
    @ValueSource(strings={"project_id='other'","run_id='other'","result_id='other'","output_hash='other'",
            "status='RUNNING'","side_effecting=0","idempotency_key=CONCAT(idempotency_key,'-other')"})
    void unmatchedOrUnfinishedExecutionCannotSupplyMissingTime(String mutation) {
        jdbc.update("UPDATE ai_ops_tool_execution_ledger SET "+mutation+" WHERE idempotency_key=?",key);
        assertNull(observation().get("startedEpoch"));
        assertEquals("MISSING",observation().get("startedEpochSource"));
    }
    @Test void existingOperationStartStillWorksWithoutLedgerAndMatchingLedgerTakesPrecedenceOverInitialization() {
        jdbc.update("UPDATE ai_ops_change_package_landing_operation_run SET started_at='2026-09-16 03:10:00' WHERE execution_key=?",key);
        assertEquals("TOOL_EXECUTION_LEDGER_CREATED_AT",observation().get("startedEpochSource"));
        jdbc.update("DELETE FROM ai_ops_tool_execution_ledger WHERE idempotency_key=?",key);
        assertEquals(1789528200L,((Number)observation().get("startedEpoch")).longValue());
        assertEquals("LANDING_OPERATION_STARTED_AT",observation().get("startedEpochSource"));
    }
}
