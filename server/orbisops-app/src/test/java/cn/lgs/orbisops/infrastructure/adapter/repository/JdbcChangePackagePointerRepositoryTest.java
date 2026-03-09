package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingCompletion;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationFailure;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcChangePackagePointerRepositoryTest {

    @Test
    void mapsAuthoritativePointer() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(jdbc);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "package_id", "cp-1",
                "status", "APPROVED",
                "version", 3,
                "package_hash", "hash-3",
                "approved_version", 3,
                "approved_package_hash", "hash-3")));

        ChangePackagePointer pointer = repository.find("cp-1").orElseThrow();

        assertEquals(ChangePackageStatus.APPROVED, pointer.status());
        assertEquals(3, pointer.version());
        assertTrue(pointer.approved());
    }

    @Test
    void statusCasGuardsStatusVersionAndHash() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ChangePackagePointer expected = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.READY_FOR_REVIEW, 2, "hash-2", 0, "");

        assertTrue(repository.compareAndSetStatus(expected, ChangePackageStatus.REVIEWING));

        verify(jdbc).update(argThat(sql -> sql.contains("WHERE package_id=? AND status=? AND version=? AND package_hash=?")),
                any(Object[].class));
    }

    @Test
    void versionAdvanceClearsApprovalAndGuardsOldPointer() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ChangePackagePointer expected = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.REJECTED, 2, "hash-2", 0, "");
        ChangePackagePointer next = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.REVISING, 3, "hash-3", 0, "");

        assertTrue(repository.compareAndSetVersion(expected, next,
                ChangePackageCurrentState.fromSnapshot(Map.of("riskLevel", "HIGH"))));

        verify(jdbc).update(argThat(sql -> sql.contains("approved_version=NULL")
                        && sql.contains("approved_package_hash=NULL")
                        && sql.contains("WHERE package_id=? AND status=? AND version=? AND package_hash=?")),
                any(Object[].class));
    }

    @Test
    void rejectsVersionAdvanceThatKeepsApprovalPointer() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(jdbc);
        ChangePackagePointer expected = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.APPROVED, 2, "hash-2", 2, "hash-2");
        ChangePackagePointer next = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.CLOSED, 3, "hash-3", 2, "hash-2");

        assertThrows(IllegalArgumentException.class, () -> repository.compareAndSetVersion(
                expected, next, ChangePackageCurrentState.fromSnapshot(Map.of("riskLevel", "HIGH"))));
    }

    @Test
    void validationFailureCasGuardsCurrentPointer() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ChangePackagePointer expected = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.VALIDATING, 2, "hash-2", 0, "");

        assertTrue(repository.compareAndSetValidationFailure(expected,
                new ChangePackageValidationFailure(
                        "NEEDS_REFINEMENT",
                        "PROOF_MISSING",
                        Map.of("reasonCode", "PROOF_MISSING"))));

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(argThat(sql -> sql.contains("validation_assessment=?")
                        && sql.contains("failure_summary_json=?")
                        && sql.contains("WHERE package_id=? AND status=? AND version=? AND package_hash=?")),
                values.capture());
        assertEquals("VALIDATION_FAILED", values.getValue()[0]);
        assertEquals("NEEDS_REFINEMENT", values.getValue()[1]);
        assertEquals("PROOF_MISSING", values.getValue()[2]);
        assertEquals("{\"reasonCode\":\"PROOF_MISSING\"}", values.getValue()[3]);
    }

    @Test
    void approvalCasFreezesCurrentVersionAndHash() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ChangePackagePointer expected = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.REVIEWING, 3, "hash-3", 0, "");

        assertTrue(repository.compareAndSetApproved(expected,
                new ChangePackageSnapshot(Map.of("version", 3), "hash-3"), "approver"));

        verify(jdbc).update(argThat(sql -> sql.contains("approved_version=?")
                        && sql.contains("approved_package_hash=?")
                        && sql.contains("WHERE package_id=? AND status=? AND version=? AND package_hash=?")),
                any(Object[].class));
    }

    @Test
    void landingStartCasAcceptsApprovedFailedPointerForGuardedRetry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ChangePackagePointer expected = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.LANDING_FAILED, 4, "hash-4", 4, "hash-4");

        assertTrue(repository.compareAndSetLandingStarted(expected, "lr-retry"));

        verify(jdbc).update(argThat(sql -> sql.contains("SET status=?, landing_run_id=?")
                        && sql.contains("approved_version=? AND approved_package_hash=?")),
                any(Object[].class));
    }

    @Test
    void landingResultCasGuardsCurrentAndApprovedPointers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ChangePackagePointer expected = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.LANDING_RUNNING, 4, "hash-4", 4, "hash-4");

        assertTrue(repository.compareAndSetLandingResult(expected,
                new ChangePackageLandingCompletion(ChangePackageStatus.LANDED, "lr-1",
                        Map.of("status", "LANDED"), Map.of())));

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(argThat(sql -> sql.contains("approved_version=? AND approved_package_hash=?")
                        && sql.contains("version=? AND package_hash=?")),
                values.capture());
        assertEquals("LANDED", values.getValue()[0]);
        assertEquals("{\"status\":\"LANDED\"}", values.getValue()[1]);
        assertEquals("lr-1", values.getValue()[2]);
        assertEquals("{}", values.getValue()[3]);
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcChangePackagePointerRepository repository = new JdbcChangePackagePointerRepository(provider);

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.find("cp-1"));
    }
}
