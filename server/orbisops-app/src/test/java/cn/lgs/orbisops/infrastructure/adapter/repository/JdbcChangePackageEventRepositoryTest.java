package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
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

class JdbcChangePackageEventRepositoryTest {

    @Test
    void appendsEventAndPreservesPayload() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageEventRepository repository = new JdbcChangePackageEventRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.append(event());

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                sql.contains("INSERT INTO ai_ops_change_package_event")), values.capture());
        assertEquals("{\"version\":1}", values.getValue()[5]);
    }

    @Test
    void readsBoundedRecentEvents() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageEventRepository repository = new JdbcChangePackageEventRepository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 9L,
                "event_id", "event-1",
                "package_id", "cp-1",
                "event_type", "PACKAGE_APPROVED",
                "actor", "approver",
                "summary", "approved",
                "payload_json", "{\"version\":2}")));

        ChangePackageEvent stored = repository.findRecent("cp-1", 2000).get(0);

        assertEquals("PACKAGE_APPROVED", stored.eventType());
        assertEquals(Map.of("version", 2), stored.payload());
        verify(jdbc).queryForList(anyString(), eq("cp-1"), eq(500));
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcChangePackageEventRepository repository = new JdbcChangePackageEventRepository(provider);

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.append(event()));
    }

    private ChangePackageEvent event() {
        return new ChangePackageEvent(0L, "event-1", "cp-1", "PACKAGE_CREATED", "ops-user",
                "created", Map.of("version", 1), null);
    }
}
