package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxDraft;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxFailurePlan;
import cn.lgs.orbisops.domain.alert.model.AlertRunRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAlertOutboxRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcAlertOutboxRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        repository = new JdbcAlertOutboxRepository(provider);
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void upsertUsesStableDispatchKeyAndPersistsTypedRequestJson() {
        repository.upsert(draft());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());

        assertTrue(sql.getValue().contains("INSERT INTO ai_ops_alert_trigger_outbox"));
        assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE"));
        assertEquals("dispatch-1", arguments.getValue()[0]);
        assertEquals("FIRST", arguments.getValue()[5]);
        assertTrue(String.valueOf(arguments.getValue()[7]).contains("agentDefinitionId"));
        assertFalse(String.valueOf(arguments.getValue()[7]).contains("OpsAgentRunRequestDTO"));
    }

    @Test
    void preemptionOnlyTargetsLowerPriorityPendingOrFailedRows() {
        repository.preemptOneLowerPriority("project-a", 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("status IN ('PENDING','FAILED')"));
        assertTrue(sql.getValue().contains("priority < ?"));
        assertTrue(sql.getValue().contains("ORDER BY priority ASC, id ASC"));
        assertTrue(sql.getValue().contains("LIMIT 1"));
    }

    @Test
    void failureUpdateUsesDomainRetryPlanInsteadOfSqlPowPolicy() {
        repository.markFailed(9L, "lease-1", new AlertOutboxFailurePlan(2, 120, false, "network"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("INTERVAL ? SECOND"));
        assertFalse(sql.getValue().contains("POW("));
        assertTrue(sql.getValue().contains("locked_token=? AND status='RUNNING'"));
        assertEquals(2, arguments.getValue()[0]);
        assertEquals(120, arguments.getValue()[1]);
        assertEquals("lease-1", arguments.getValue()[4]);
    }

    @Test
    void successUpdateRequiresLeaseOwnershipAndRunningState() {
        repository.markSucceeded(9L, "lease-1", "run-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("WHERE id=? AND locked_token=? AND status='RUNNING'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchableQueryOrdersByPriorityAndBoundedIdOrder() {
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(RowMapper.class), any(Object[].class));

        repository.listDispatchable(8, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("ORDER BY priority DESC, id ASC"));
        assertTrue(sql.getValue().contains("retry_count < ?"));
    }

    private AlertOutboxDraft draft() {
        return new AlertOutboxDraft(
                "dispatch-1",
                7L,
                "project-a",
                "fingerprint-1",
                "aggregate-1",
                AlertAggregateEventType.FIRST,
                50,
                request(),
                Map.of("status", "firing"));
    }

    private AlertRunRequest request() {
        return new AlertRunRequest(
                "",
                "alertmanager:rule-7",
                "project-a",
                "agent-a",
                3,
                "{}",
                "",
                "analyse alert",
                30,
                "5m",
                true,
                null,
                5,
                120,
                20,
                false,
                "",
                "",
                "WORKFLOW",
                "ALERTMANAGER",
                "fingerprint-1");
    }
}
