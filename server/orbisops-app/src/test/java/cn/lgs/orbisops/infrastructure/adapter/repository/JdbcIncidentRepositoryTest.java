package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.incident.model.IncidentAlertDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcIncidentRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcIncidentRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        repository = new JdbcIncidentRepository(provider);
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class));
    }

    @Test
    void alertUpsertOwnsProjectScopedDedupAndAtomicOccurrenceIncrement() {
        repository.upsertAlert(new IncidentAlertDraft(
                "incident-alert",
                "project-a",
                "HighErrorRate",
                "WARN",
                "payment",
                "ALERTMANAGER",
                "fingerprint-1",
                "project-a:rule-7:fingerprint-1",
                "run-1",
                "error rate high",
                "{}",
                Map.of("ruleId", 7L),
                List.of("payment"),
                false));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sql.capture(), args.capture());
        int upsertIndex = -1;
        for (int i = 0; i < sql.getAllValues().size(); i++) {
            if (sql.getAllValues().get(i).contains("occurrence_count = occurrence_count + 1")) {
                upsertIndex = i;
                break;
            }
        }
        assertTrue(upsertIndex >= 0);
        assertTrue(sql.getAllValues().get(upsertIndex).contains(
                "resolved_at = IF(status IN ('RESOLVED','CLOSED','REVIEWED'), NULL, resolved_at)"));
        Object[] values = args.getAllValues().get(upsertIndex);
        assertEquals("project-a", values[1]);
        assertEquals("project-a:rule-7:fingerprint-1", values[7]);
    }

    @Test
    void assignOwnerPersistsDedicatedResponsibilityColumn() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.assignOwner("incident-1", "user-7"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sql.capture(), args.capture());
        int updateIndex = -1;
        for (int i = 0; i < sql.getAllValues().size(); i++) {
            if (sql.getAllValues().get(i).contains("SET owner_user_id=?")) {
                updateIndex = i;
                break;
            }
        }
        assertTrue(updateIndex >= 0);
        assertEquals("user-7", args.getAllValues().get(updateIndex)[0]);
        assertEquals("incident-1", args.getAllValues().get(updateIndex)[1]);
    }

    @Test
    void listQueryKeepsProjectAndStatusIsolationInInfrastructure() {
        repository.list("project-a", IncidentStatus.OPEN, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class));
        assertTrue(sql.getValue().contains("project_id=? AND status=?"));
    }
}
