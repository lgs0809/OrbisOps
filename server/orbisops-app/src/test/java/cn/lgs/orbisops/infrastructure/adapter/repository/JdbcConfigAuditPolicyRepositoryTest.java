package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.domain.audit.model.AuditPolicyStatus;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditPolicySnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcConfigAuditPolicyRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void findDefaultsBlankProjectToGlobalAndOwnsPolicySelect() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        ConfigAuditPolicySnapshot snapshot = snapshot("GLOBAL", 180);
        doReturn(List.of(snapshot)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        JdbcConfigAuditPolicyRepository repository = new JdbcConfigAuditPolicyRepository(provider);

        ConfigAuditPolicySnapshot found = repository.find("").orElseThrow();

        assertEquals(snapshot, found);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("FROM ai_ops_audit_policy"));
        assertEquals("GLOBAL", args.getValue()[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveOwnsUpsertAndReadsBackTypedSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
        doReturn(List.of(snapshot("project-1", 90))).when(jdbc).query(
                anyString(), any(RowMapper.class), any(Object[].class));
        JdbcConfigAuditPolicyRepository repository = new JdbcConfigAuditPolicyRepository(provider);

        ConfigAuditPolicySnapshot saved = repository.save(policy("project-1", 90));

        assertEquals(90, saved.policy().retentionDays());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("INSERT INTO ai_ops_audit_policy"));
        assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE"));
        assertEquals("project-1", args.getValue()[0]);
        assertEquals(90, args.getValue()[1]);
        assertEquals(1, args.getValue()[2]);
    }

    @Test
    void absentJdbcReportsNonPersistent() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcConfigAuditPolicyRepository repository = new JdbcConfigAuditPolicyRepository(provider);

        assertFalse(repository.persistent());
        assertTrue(repository.find("GLOBAL").isEmpty());
    }

    private ConfigAuditPolicySnapshot snapshot(String projectId, int retentionDays) {
        return new ConfigAuditPolicySnapshot(policy(projectId, retentionDays), true, "now");
    }

    private AuditPolicy policy(String projectId, int retentionDays) {
        return new AuditPolicy(projectId, retentionDays, true, true, true, true, AuditPolicyStatus.ENABLED);
    }
}
