package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.audit.model.ConfigAuditCriteria;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditDraft;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcConfigAuditRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void appendOwnsFixedInsertAndReadBackSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcConfigAuditRepository repository = new JdbcConfigAuditRepository(provider, true, false, "");
        ConfigAuditDraft draft = draft("audit-1");
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
        doReturn(List.of(entry("audit-1"))).when(jdbc).query(
                anyString(), any(RowMapper.class), any(Object[].class));

        ConfigAuditEntry saved = repository.append(draft);

        assertEquals("audit-1", saved.auditId());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("INSERT INTO ai_ops_config_audit"));
        assertEquals("audit-1", args.getValue()[0]);
        assertEquals("project-1", args.getValue()[1]);
        assertEquals("HIGH", args.getValue()[7]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchBuildsBoundedTypedFiltersAndLimit() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        JdbcConfigAuditRepository repository = new JdbcConfigAuditRepository(provider, true, false, "");
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        repository.search(new ConfigAuditCriteria(
                "project-1", "alice", "agent-1", "skill", "delete", "HIGH",
                "2026-07-01", "2026-07-31", 50));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("FROM ai_ops_config_audit"));
        assertTrue(sql.getValue().contains("(operator_id=? OR operator_name=?)"));
        assertTrue(sql.getValue().contains("create_time>=?"));
        assertTrue(sql.getValue().contains("ORDER BY id DESC LIMIT ?"));
        assertEquals("project-1", args.getValue()[0]);
        assertEquals("alice", args.getValue()[1]);
        assertEquals("alice", args.getValue()[2]);
        assertEquals(50, args.getValue()[9]);
    }

    @Test
    void productionWithoutJdbcFailsClosed() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcConfigAuditRepository repository = new JdbcConfigAuditRepository(provider, true, false, "");

        assertTrue(assertThrows(IllegalStateException.class,
                () -> repository.search(ConfigAuditCriteria.empty())).getMessage()
                .contains("禁止返回空审计列表"));
    }

    @Test
    void explicitTestProfileAllowsMemoryFallback() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcConfigAuditRepository repository = new JdbcConfigAuditRepository(provider, true, true, "test");

        repository.append(draft("audit-memory"));

        assertEquals(1, repository.search(ConfigAuditCriteria.empty()).size());
        assertEquals("DEGRADED_MEMORY", repository.readiness().status());
    }

    @Test
    void memoryFallbackAppliesTheSameTimeWindowAsJdbcSearch() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcConfigAuditRepository repository = new JdbcConfigAuditRepository(provider, true, true, "test");
        repository.append(draft("audit-memory"));

        assertEquals(1, repository.search(new ConfigAuditCriteria(
                "project-1", "", "", "", "", "",
                "2026-07-01 00:00:00", "2026-07-31 23:59:59", 100)).size());
        assertEquals(0, repository.search(new ConfigAuditCriteria(
                "project-1", "", "", "", "", "",
                "2026-08-01 00:00:00", "", 100)).size());
        assertEquals(0, repository.search(new ConfigAuditCriteria(
                "project-1", "", "", "", "", "",
                "", "2026-07-01 00:00:00", 100)).size());
    }

    private ConfigAuditDraft draft(String auditId) {
        return new ConfigAuditDraft(
                auditId, "project-1", "agent-1", "skill", "delete", "skill", "skill-1",
                "HIGH", "SUCCESS", "u1", "alice", "admin", "127.0.0.1", "trace-1",
                "{}", "{}", LocalDateTime.of(2026, 7, 23, 18, 0));
    }

    private ConfigAuditEntry entry(String auditId) {
        return ConfigAuditEntry.from(draft(auditId));
    }
}
