package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcChangePackageVersionRepositoryTest {

    @Test
    void appendsImmutableVersionWithoutUpsert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageVersionRepository repository = new JdbcChangePackageVersionRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.append(version());

        verify(jdbc).update(org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("INSERT INTO ai_ops_change_package_version")
                                && !sql.toUpperCase().contains("UPDATE")),
                any(Object[].class));
    }

    @Test
    void mapsStoredVersionToTypedRecord() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageVersionRepository repository = new JdbcChangePackageVersionRepository(jdbc);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 17, 10, 30);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 7L,
                "package_id", "cp-1",
                "version", 2,
                "package_hash", "hash-2",
                "status", "READY_FOR_REVIEW",
                "snapshot_json", "{\"packageId\":\"cp-1\"}",
                "change_summary", "validation proof writeback",
                "created_by", "ops-user",
                "create_time", Timestamp.valueOf(createdAt))));

        ChangePackageVersion stored = repository.find("cp-1", 2).orElseThrow();

        assertEquals(7L, stored.id());
        assertEquals("hash-2", stored.packageHash());
        assertEquals("READY_FOR_REVIEW", stored.status());
        assertEquals("cp-1", stored.snapshot().toMap().get("packageId"));
        assertEquals("hash-2", stored.snapshot().packageHash());
        assertEquals(createdAt, stored.createdAt());
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcChangePackageVersionRepository repository = new JdbcChangePackageVersionRepository(provider);

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.find("cp-1", 1));
    }

    private ChangePackageVersion version() {
        return new ChangePackageVersion(0L, "cp-1", 1, "hash-1", "DRAFT",
                new ChangePackageSnapshot(Map.of("packageId", "cp-1"), "hash-1"),
                "initial", "ops-user", null);
    }
}
