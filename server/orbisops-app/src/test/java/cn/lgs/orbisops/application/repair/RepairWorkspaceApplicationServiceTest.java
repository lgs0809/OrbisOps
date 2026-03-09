package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.adapter.repository.IRepairWorkspaceRepository;
import cn.lgs.orbisops.domain.repair.model.RepairArtifactValidation;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairVerificationResult;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCandidate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepairWorkspaceApplicationServiceTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";
    private static final String DIFF_HASH = "a".repeat(64);

    @Test
    void createsVerifiedWorkspaceFromDeploymentAndAuditsInTransaction() {
        IRepairWorkspaceRepository repository = mock(IRepairWorkspaceRepository.class);
        RepairSourceCatalogPort sources = mock(RepairSourceCatalogPort.class);
        RepairWorkspaceExecutionPort execution = mock(RepairWorkspaceExecutionPort.class);
        RepairAuditPort audit = mock(RepairAuditPort.class);
        when(sources.findService("project-1", "service-1")).thenReturn(Optional.of(service()));
        when(sources.findRepository("project-1", "repo-1")).thenReturn(Optional.of(source()));
        when(sources.resolveDeployment("project-1", "prod", "service-1"))
                .thenReturn(Optional.of(deployment()));
        when(execution.createAndVerify(any())).thenAnswer(invocation -> {
            RepairExecutionCommand command = invocation.getArgument(0);
            return workspace(command.workspaceId(), RepairWorkspaceStatus.VERIFIED, REPAIR);
        });
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RepairWorkspaceApplicationService application = application(
                repository, sources, execution, audit, true);
        RepairWorkspaceCandidate candidate = new RepairWorkspaceCandidate(
                "project-1", "service-1", "prod", "fix", patch(), "");

        RepairWorkspace result = application.createAndVerify(candidate, "alice");

        assertEquals(BASE, result.baseCommit());
        assertEquals(RepairWorkspaceStatus.VERIFIED, result.status());
        ArgumentCaptor<RepairExecutionCommand> command = ArgumentCaptor.forClass(RepairExecutionCommand.class);
        verify(execution).createAndVerify(command.capture());
        assertEquals(BASE, command.getValue().baseCommit());
        assertEquals("alice", command.getValue().actor());
        ArgumentCaptor<RepairAuditEvent> event = ArgumentCaptor.forClass(RepairAuditEvent.class);
        verify(audit).record(event.capture());
        assertEquals("create", event.getValue().action());
    }

    @Test
    void delegatesWriterLeaseAndStatusThroughRepository() {
        IRepairWorkspaceRepository repository = mock(IRepairWorkspaceRepository.class);
        when(repository.claimWriter("repair-1", "run-1", 300L)).thenReturn(
                new RepairWriterLease("repair-1", "project-1", "run-1", "claim-1", 2, "later", 3));
        RepairWorkspaceApplicationService application = application(
                repository, mock(RepairSourceCatalogPort.class), mock(RepairWorkspaceExecutionPort.class),
                mock(RepairAuditPort.class), true);

        assertEquals(2, application.claimWriter("repair-1", "run-1").fencingToken());
        application.markDirty("repair-1", "run-1");
        application.markTesting("repair-1", "run-1");
        application.recordTestOutcome("repair-1", "run-1", true);

        verify(repository).updateWriterOwnedStatus("repair-1", "run-1", RepairWorkspaceStatus.DIRTY);
        verify(repository).updateWriterOwnedStatus("repair-1", "run-1", RepairWorkspaceStatus.TESTING);
        verify(repository).updateWriterOwnedStatus("repair-1", "run-1", RepairWorkspaceStatus.TEST_PASSED);
    }

    @Test
    void commitsThenVerifiesExpectedDiffAndTestProof() {
        IRepairWorkspaceRepository repository = mock(IRepairWorkspaceRepository.class);
        RepairWorkspaceExecutionPort execution = mock(RepairWorkspaceExecutionPort.class);
        RepairAuditPort audit = mock(RepairAuditPort.class);
        RepairWorkspace active = workspace("repair-1", RepairWorkspaceStatus.ACTIVE, "");
        RepairDiffSnapshot diff = diff("repair-1", BASE);
        when(repository.find("repair-1")).thenReturn(Optional.of(active),
                Optional.of(active.committed(REPAIR, diff.changedFiles(), "later")));
        when(repository.claimWriter("repair-1", "run-1", 300L)).thenReturn(
                new RepairWriterLease("repair-1", "project-1", "run-1", "claim-1", 1, "later", 1));
        when(execution.computeDiff(any())).thenReturn(diff);
        when(execution.commit(active, "fix repair-1", "alice")).thenReturn(
                new RepairCommitResult(diff, REPAIR, RepairWorkspaceStatus.COMMITTED, "alice"));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RepairWorkspaceApplicationService application = application(
                repository, mock(RepairSourceCatalogPort.class), execution, audit, true);

        RepairCommitResult committed = application.commitRepair(
                "repair-1", "fix repair-1", "alice", "run-1");
        RepairVerificationResult verified = application.verify(
                "repair-1", DIFF_HASH, List.of("module/src/App.java"), "proof-1", "alice");

        assertEquals(REPAIR, committed.repairCommit());
        assertEquals(RepairWorkspaceStatus.VERIFIED, verified.status());
        assertEquals("proof-1", verified.testProofHash());
        verify(audit, org.mockito.Mockito.times(2)).record(any());
    }

    @Test
    void validatesArtifactAndCleansMissingWorkspaceIdempotently() {
        IRepairWorkspaceRepository repository = mock(IRepairWorkspaceRepository.class);
        RepairWorkspaceExecutionPort execution = mock(RepairWorkspaceExecutionPort.class);
        RepairWorkspace verified = new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE, REPAIR,
                RepairWorkspaceStatus.VERIFIED, "fix", patch(), List.of("module/src/App.java"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", "/tmp/artifact.jar", "b".repeat(64), 10L,
                "alice", "now", "now");
        when(repository.find("repair-1")).thenReturn(Optional.of(verified));
        when(execution.validateArtifact(verified, "/tmp/artifact.jar", "b".repeat(64))).thenReturn(
                new RepairArtifactValidation(
                        "repair-1", "/tmp/artifact.jar", "b".repeat(64), 10L,
                        BASE, REPAIR, verified.changedFiles(), "MAVEN_VERIFY", 0));
        RepairWorkspaceApplicationService application = application(
                repository, mock(RepairSourceCatalogPort.class), execution,
                mock(RepairAuditPort.class), true);

        assertEquals(10L, application.validateArtifact(
                "repair-1", "project-1", "service-1", "/tmp/artifact.jar", "b".repeat(64)).artifactSize());
        assertEquals("NO_TEMP_RESOURCE",
                application.cleanupIfPresent("missing", true).status());
    }

    @Test
    void failsClosedWhenFeatureDisabledOrWorkspaceMissing() {
        RepairWorkspaceApplicationService disabled = application(
                mock(IRepairWorkspaceRepository.class), mock(RepairSourceCatalogPort.class),
                mock(RepairWorkspaceExecutionPort.class), mock(RepairAuditPort.class), false);
        assertEquals("代码修复沙箱未启用",
                assertThrows(IllegalStateException.class, () -> disabled.createAndVerify(
                        new RepairWorkspaceCandidate(
                                "project-1", "service-1", "prod", "fix", patch(), BASE),
                        "alice")).getMessage());

        RepairWorkspaceApplicationService enabled = application(
                mock(IRepairWorkspaceRepository.class), mock(RepairSourceCatalogPort.class),
                mock(RepairWorkspaceExecutionPort.class), mock(RepairAuditPort.class), true);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> enabled.get("missing")).getMessage().contains("NOT_FOUND"));
    }

    private RepairWorkspaceApplicationService application(
            IRepairWorkspaceRepository repository,
            RepairSourceCatalogPort sources,
            RepairWorkspaceExecutionPort execution,
            RepairAuditPort audit,
            boolean enabled) {
        RepairTransactionPort transaction = new RepairTransactionPort() {
            @Override
            public <T> T required(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        return new RepairWorkspaceApplicationService(
                repository, sources, execution, audit, transaction, enabled, "docker", 300L);
    }

    private ProjectService service() {
        return new ProjectService(
                "service-1", "project-1", "service", "repo-1", "module",
                BuildProfile.MAVEN_VERIFY, "module/target/app.jar", "", "", List.of(),
                "READY", "now", "now");
    }

    private SourceRepository source() {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo",
                "HEAD", BASE, "READY", "admin", "now", "now");
    }

    private DeploymentRevision deployment() {
        return new DeploymentRevision(
                "project-1:prod:service-1", "project-1", "repo-1", "prod", "service-1",
                BASE, "image:v1", "admin", "now", "now");
    }

    private RepairWorkspace workspace(
            String id,
            RepairWorkspaceStatus status,
            String verifiedCommit) {
        return new RepairWorkspace(
                id, "project-1", "service-1", "repo-1", "prod", BASE, verifiedCommit,
                status, "fix", patch(), List.of("module/src/App.java"), "MAVEN_VERIFY",
                "mvn test", 0, "ok", "", "", 0L, "alice", "now", "now");
    }

    private RepairDiffSnapshot diff(String id, String head) {
        return new RepairDiffSnapshot(
                id, BASE, head, List.of("module/src/App.java"), "1 file changed",
                DIFF_HASH, 100);
    }

    private String patch() {
        return """
                --- a/module/src/App.java
                +++ b/module/src/App.java
                @@ -1 +1 @@
                -old
                +new
                """;
    }
}
