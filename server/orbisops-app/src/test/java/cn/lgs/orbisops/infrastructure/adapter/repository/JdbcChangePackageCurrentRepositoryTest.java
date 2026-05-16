package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentQuery;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcChangePackageCurrentRepositoryTest {

    @Test
    void mapsAuthoritativeCurrentRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageCurrentRepository repository = new JdbcChangePackageCurrentRepository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 7L), Map.entry("package_id", "cp-7"), Map.entry("session_id", "s-1"),
                Map.entry("project_id", "project-a"), Map.entry("preparation_agent_version", 2),
                Map.entry("package_type", "MCP_OPERATION_PACKAGE"), Map.entry("status", "APPROVED"),
                Map.entry("version", 3), Map.entry("package_hash", "hash-3"),
                Map.entry("approved_version", 3), Map.entry("approved_package_hash", "hash-3"),
                Map.entry("approved_snapshot_json",
                        "{\"packageId\":\"cp-7\",\"version\":3,\"packageHash\":\"hash-3\",\"projectId\":\"project-a\"}"),
                Map.entry("risk_level", "HIGH"), Map.entry("objective", "repair checkout"),
                Map.entry("create_time", Timestamp.valueOf(LocalDateTime.of(2026, 7, 17, 8, 30))))));

        ChangePackageCurrent current = repository.find("cp-7").orElseThrow();

        assertEquals(7L, current.id());
        assertEquals(ChangePackageStatus.APPROVED, current.status());
        assertEquals(ChangePackageType.MCP_OPERATION_PACKAGE, current.packageType());
        assertEquals("HIGH", current.state().value(cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField.RISK_LEVEL));
        assertTrue(current.pointer().approved());
        assertEquals("hash-3", current.approvedSnapshot().packageHash());
        assertEquals("project-a", current.approvedSnapshot().toMap().get("projectId"));
    }

    @Test
    void approvedPointerWithoutFrozenSnapshotFailsClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageCurrentRepository repository = new JdbcChangePackageCurrentRepository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 7L), Map.entry("package_id", "cp-7"),
                Map.entry("project_id", "project-a"), Map.entry("preparation_agent_version", 2),
                Map.entry("package_type", "MCP_OPERATION_PACKAGE"), Map.entry("status", "APPROVED"),
                Map.entry("version", 3), Map.entry("package_hash", "hash-3"),
                Map.entry("approved_version", 3), Map.entry("approved_package_hash", "hash-3"),
                Map.entry("risk_level", "HIGH"))));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> repository.find("cp-7"));

        assertEquals("CHANGE_PACKAGE_VERSION_SNAPSHOT_REQUIRED", error.getMessage());
    }

    @Test
    void unapprovedPointerWithStaleFrozenSnapshotFailsClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageCurrentRepository repository = new JdbcChangePackageCurrentRepository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 7L), Map.entry("package_id", "cp-7"),
                Map.entry("project_id", "project-a"), Map.entry("preparation_agent_version", 2),
                Map.entry("package_type", "MCP_OPERATION_PACKAGE"), Map.entry("status", "DRAFT"),
                Map.entry("version", 1), Map.entry("package_hash", "hash-1"),
                Map.entry("risk_level", "MEDIUM"),
                Map.entry("approved_snapshot_json",
                        "{\"packageId\":\"cp-7\",\"version\":1,\"packageHash\":\"hash-1\"}"))));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository.find("cp-7"));

        assertEquals("CHANGE_PACKAGE_UNAPPROVED_SNAPSHOT_PERSISTED", error.getMessage());
    }

    @Test
    void insertsTypedCurrentSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageCurrentRepository repository = new JdbcChangePackageCurrentRepository(jdbc);
        ChangePackageCurrent current = ChangePackageCurrent.draft(
                new ChangePackagePointer("cp-1", ChangePackageStatus.DRAFT, 1, "hash-1", 0, ""),
                "session-1", "", "project-a", "prep-agent", 4, ChangePackageType.GIT_BRANCH_REPAIR,
                ChangePackageCurrentState.fromSnapshot(Map.of(
                        "riskLevel", "MEDIUM", "objective", "repair payment", "branchName", "ops/repair/cp-1")),
                "alice");

        repository.insert(current);

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        assertEquals("cp-1", values.getValue()[0]);
        assertEquals("project-a", values.getValue()[3]);
        assertEquals("GIT_BRANCH_REPAIR", values.getValue()[6]);
        assertEquals("MEDIUM", values.getValue()[13]);
        assertEquals("alice", values.getValue()[10 + ChangePackageCurrentStateJdbcMapper.fieldCount()]);
    }

    @Test
    void appliesProjectSessionAndStatusFilters() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageCurrentRepository repository = new JdbcChangePackageCurrentRepository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertTrue(repository.findAll(new ChangePackageCurrentQuery(
                "project-a", "session-1", "", ChangePackageStatus.REVIEWING, 20)).isEmpty());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("project_id=?"));
        assertTrue(sql.getValue().contains("session_id=?"));
        assertTrue(sql.getValue().contains("status=?"));
        assertEquals(List.of("project-a", "session-1", "REVIEWING", 20), List.of(args.getValue()));
    }

    @Test
    void readinessUsesAuthoritativeTable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackageCurrentRepository repository = new JdbcChangePackageCurrentRepository(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        repository.verifyReadable();

        verify(jdbc).queryForObject("SELECT COUNT(1) FROM ai_ops_change_package", Integer.class);
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcChangePackageCurrentRepository repository = new JdbcChangePackageCurrentRepository(provider);

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.find("cp-1"));
        assertThrows(IllegalStateException.class, repository::verifyReadable);
    }
}
