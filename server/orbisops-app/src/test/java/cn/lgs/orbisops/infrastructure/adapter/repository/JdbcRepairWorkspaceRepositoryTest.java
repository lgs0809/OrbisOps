package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcRepairWorkspaceRepositoryTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void memorySnapshotSupportsSaveFindListAndUpdate() {
        JdbcRepairWorkspaceRepository repository =
                new JdbcRepairWorkspaceRepository((JdbcTemplate) null, false);
        RepairWorkspace workspace = workspace(RepairWorkspaceStatus.ACTIVE);

        repository.save(workspace);
        repository.update(workspace.withStatus(RepairWorkspaceStatus.DIRTY, "later"));

        assertEquals(RepairWorkspaceStatus.DIRTY,
                repository.find("repair-1").orElseThrow().status());
        assertEquals(1, repository.list("project-1").size());
        assertTrue(repository.list("other").isEmpty());
        assertEquals("REPAIR_WORKSPACE_WRITER_STORE_UNAVAILABLE",
                assertThrows(IllegalStateException.class,
                        () -> repository.claimWriter("repair-1", "run-1", 300)).getMessage());
    }

    @Test
    void jdbcOwnsFixedUpsertAndTypedJson() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
        JdbcRepairWorkspaceRepository repository = new JdbcRepairWorkspaceRepository(jdbc, true);

        repository.save(workspace(RepairWorkspaceStatus.VERIFIED));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("INSERT INTO ai_ops_repair_workspace"));
        assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE"));
        assertEquals("[\"module/src/App.java\"]", args.getValue()[10]);
        assertEquals("VERIFIED", args.getValue()[7]);
    }

    @Test
    void writerClaimUsesCasFencingAndReturnsTypedLease() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0, 1);
        when(jdbc.queryForList(anyString(), eq("repair-1"), eq("run-1"))).thenReturn(List.of(Map.of(
                "project_id", "project-1",
                "writer_lease_owner", "run-1",
                "writer_lease_token", "claim-1",
                "writer_fencing_token", 3L,
                "writer_lease_expires_at", "2026-07-24 10:00:00",
                "state_version", 4L)));
        JdbcRepairWorkspaceRepository repository = new JdbcRepairWorkspaceRepository(jdbc, true);

        RepairWriterLease lease = repository.claimWriter("repair-1", "run-1", 300);

        assertEquals(3L, lease.fencingToken());
        assertEquals("claim-1", lease.leaseToken());
        assertEquals(4L, lease.stateVersion());
        verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void writerBusyAndLostLeaseFailClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0, 0);
        JdbcRepairWorkspaceRepository repository = new JdbcRepairWorkspaceRepository(jdbc, true);

        assertTrue(assertThrows(IllegalStateException.class,
                () -> repository.claimWriter("repair-1", "run-1", 300))
                .getMessage().contains("WRITER_BUSY"));

        JdbcTemplate statusJdbc = mock(JdbcTemplate.class);
        when(statusJdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        JdbcRepairWorkspaceRepository statusRepository =
                new JdbcRepairWorkspaceRepository(statusJdbc, true);
        assertEquals("REPAIR_WORKSPACE_WRITER_LEASE_LOST",
                assertThrows(IllegalStateException.class,
                        () -> statusRepository.updateWriterOwnedStatus(
                                "repair-1", "run-1", RepairWorkspaceStatus.DIRTY)).getMessage());
    }

    private RepairWorkspace workspace(RepairWorkspaceStatus status) {
        return new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE,
                status == RepairWorkspaceStatus.VERIFIED
                        ? "89abcdef0123456789abcdef0123456789abcdef" : "",
                status, "fix", "patch", List.of("module/src/App.java"), "MAVEN_VERIFY",
                "mvn test", 0, "ok", "", "", 0L, "alice", "now", "now");
    }
}
