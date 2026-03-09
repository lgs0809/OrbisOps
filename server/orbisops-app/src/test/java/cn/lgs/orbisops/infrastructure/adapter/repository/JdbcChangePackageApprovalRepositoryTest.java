package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApproval;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcChangePackageApprovalRepositoryTest {

    @Test
    void savesIdempotentApproverDecision() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageApprovalRepository repository = new JdbcChangePackageApprovalRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.saveDecision(approval());

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                sql.contains("ON DUPLICATE KEY UPDATE") && sql.contains("decision=VALUES(decision)")),
                values.capture());
        assertEquals("{\"comment\":\"checked\"}", values.getValue()[10]);
    }

    @Test
    void countsDistinctApproversForExactVersionAndHash() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageApprovalRepository repository = new JdbcChangePackageApprovalRepository(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("cp-1"), eq(2), eq("hash-2")))
                .thenReturn(2);

        assertEquals(2, repository.countDistinctApproved("cp-1", 2, "hash-2"));
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("COUNT(DISTINCT approver)") && sql.contains("decision='APPROVED'")),
                eq(Integer.class), eq("cp-1"), eq(2), eq("hash-2"));
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcChangePackageApprovalRepository repository = new JdbcChangePackageApprovalRepository(provider);

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.saveDecision(approval()));
    }

    private ChangePackageApproval approval() {
        return new ChangePackageApproval("approval-1", "cp-1", "project-1", 2, "hash-2", "HIGH",
                "approver-a", "approver", "APPROVED", false, Map.of("comment", "checked"));
    }
}
