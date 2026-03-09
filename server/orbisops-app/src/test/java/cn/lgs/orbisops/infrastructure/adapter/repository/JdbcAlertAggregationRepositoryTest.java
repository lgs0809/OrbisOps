package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateState;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationAction;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationPlan;
import cn.lgs.orbisops.domain.alert.model.AlertSummaryClaim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAlertAggregationRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcAlertAggregationRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        repository = new JdbcAlertAggregationRepository(provider);
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void duplicatePlanUsesVersionCasAndBoundedSummarySchedule() {
        repository.apply(new AlertAggregationPlan(
                "aggregate-1",
                AlertAggregationAction.SCHEDULE_SUMMARY,
                AlertAggregateEventType.DUPLICATE,
                AlertAggregateState.FIRING,
                "WARNING",
                50,
                List.of("mysql", "redis"),
                Map.of("status", "firing"),
                120,
                900,
                5));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("pending_summary_count=pending_summary_count+1"));
        assertTrue(sql.getValue().contains("WHERE aggregate_key=? AND version=?"));
        assertEquals(900, arguments.getValue()[4]);
        assertEquals(120, arguments.getValue()[6]);
        assertEquals(5L, arguments.getValue()[8]);
    }

    @Test
    void summaryAckUsesClaimOwnershipAndRemainingPendingCount() {
        repository.acknowledgeSummary(new AlertSummaryClaim(
                "aggregate-1", "dispatch-1", "project-a", 7L, "fingerprint-1",
                "WARNING", 50, 8, 3, Map.of(), List.of("mysql"), 10, "claim-1"), 120);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("pending_summary_count=GREATEST(0, pending_summary_count-?)"));
        assertTrue(sql.getValue().contains("CASE WHEN pending_summary_count > 0"));
        assertTrue(sql.getValue().contains("WHERE aggregate_key=? AND summary_claim_token=?"));
        assertEquals("claim-1", arguments.getValue()[3]);
    }

    @Test
    void dueSummaryQueryRetainsSkipLockedOrdering() {
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));

        repository.lockDueSummaries(20, 120);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).query(
                sql.capture(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(sql.getValue().contains("ORDER BY severity_rank DESC"));
    }

    @Test
    void claimCasUsesVersionAndPendingSummaryCount() {
        AlertAggregateSnapshot aggregate = new AlertAggregateSnapshot(
                "aggregate-1", "project-a", 7L, "fingerprint-1",
                AlertAggregateState.FIRING, "WARNING", 50,
                8, 3, List.of("mysql"), Map.of(), 9);

        repository.claimSummary(aggregate, "claim-1", 120);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("version=? AND pending_summary_count=?"));
        assertEquals(10L, arguments.getValue()[1]);
        assertEquals(9L, arguments.getValue()[3]);
        assertEquals(3, arguments.getValue()[4]);
    }
}
