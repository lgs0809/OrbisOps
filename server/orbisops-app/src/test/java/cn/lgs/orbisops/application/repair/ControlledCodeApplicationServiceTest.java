package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControlledCodeApplicationServiceTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";

    @Test
    void readsRepositoryAndWorkspaceWithMaskingLineNumbersAndObservation() {
        Fixture fixture = fixture();
        when(fixture.sources.readFile("project-1", "repo-1", "HEAD", "application.yml"))
                .thenReturn(new SourceFile(
                        "repo-1", BASE, "application.yml", 40,
                        "password: abc123\nhost: 10.0.0.1\n"));
        when(fixture.files.read(Path.of("/tmp/repair-1"), "src.txt", true))
                .thenReturn("hello world\n");

        ControlledCodeResult.Read repository = fixture.application.read(
                new ControlledCodeCommands.Read(
                        "project-1", "repo-1", "HEAD", "", "application.yml", 1, 10),
                "alice");
        ControlledCodeResult.Read workspace = fixture.application.read(
                new ControlledCodeCommands.Read(
                        "", "", "", "repair-1", "src.txt", 1, 10),
                "alice");

        assertEquals("password:***", repository.lines().get(0).text());
        assertEquals(1, repository.lines().get(0).line());
        assertEquals("hello world", workspace.lines().get(0).text());
        verify(fixture.audit, org.mockito.Mockito.times(2)).record(any());
    }

    @Test
    void editRequiresReadThenClaimsWriterRechecksAndMarksDirty() {
        Fixture fixture = fixture();
        when(fixture.files.read(Path.of("/tmp/repair-1"), "src.txt", true))
                .thenReturn("hello world\n");

        assertThrows(SecurityException.class, () -> fixture.application.edit(
                new ControlledCodeCommands.Edit(
                        "repair-1", "src.txt", "world", "ops", false, ""),
                "alice"));

        fixture.application.read(new ControlledCodeCommands.Read(
                "", "", "", "repair-1", "src.txt", 1, 20), "alice");
        ControlledCodeResult.Mutation result = fixture.application.edit(
                new ControlledCodeCommands.Edit(
                        "repair-1", "src.txt", "world", "ops", false, ""),
                "alice");

        assertEquals(1, result.replacements());
        verify(fixture.workspaces).claimWriter("repair-1", "actor:alice");
        verify(fixture.files).write(Path.of("/tmp/repair-1"), "src.txt", "hello ops\n");
        verify(fixture.workspaces).markDirty("repair-1", "actor:alice");
    }

    @Test
    void successfulBoundTestRecordsToolResultProofAndWorkspaceOutcome() {
        Fixture fixture = fixture();
        when(fixture.files.directory(Path.of("/tmp/repair-1"), "")).thenReturn(Path.of("/tmp/repair-1"));
        when(fixture.files.execute(
                eq(Path.of("/tmp/repair-1")), eq(List.of("mvn", "test")),
                eq(30_000), eq(256 * 1024)))
                .thenReturn(new ControlledCodeFilePort.ProcessOutput(
                        0, "password: abc123\nBUILD SUCCESS\n", false, 120));
        when(fixture.toolResults.available()).thenReturn(true);
        when(fixture.toolResults.record(any())).thenReturn(
                new ControlledCodeToolResultPort.StoredToolResult(
                        "tool-result-1", false, "db:tool-result-1", "1".repeat(64)));
        when(fixture.proofs.available()).thenReturn(true);

        ControlledCodeResult.Bash result = fixture.application.bash(
                new ControlledCodeCommands.Bash(
                        "project-1", "repo-1", "repair-1", "session-1", "run-1",
                        "mvn test", ControlledCodeEffect.TEST_OR_BUILD, "", 30_000,
                        "cp-1", 1, "package-hash", "HIGH"),
                "alice");

        assertEquals(0, result.exitCode());
        assertEquals("tool-result-1", result.resultId());
        assertEquals("1".repeat(64), result.outputHash());
        assertTrue(result.stdoutPreview().contains("password:***"));
        verify(fixture.workspaces).claimWriter("repair-1", "run:run-1");
        verify(fixture.workspaces).markTesting("repair-1", "run:run-1");
        verify(fixture.workspaces).recordTestOutcome("repair-1", "run:run-1", true);
        ArgumentCaptor<ControlledCodeProofPort.ControlledCodeProof> proof =
                ArgumentCaptor.forClass(ControlledCodeProofPort.ControlledCodeProof.class);
        verify(fixture.proofs).record(proof.capture(), eq("alice"));
        assertEquals("cp-1", proof.getValue().packageId());
        assertEquals("tool-result-1", proof.getValue().externalRunId());
    }

    @Test
    void boundTestFailsClosedWhenProofStoreUnavailable() {
        Fixture fixture = fixture();
        when(fixture.files.directory(Path.of("/tmp/repair-1"), "")).thenReturn(Path.of("/tmp/repair-1"));
        when(fixture.files.execute(any(), any(), anyInt(), anyInt()))
                .thenReturn(new ControlledCodeFilePort.ProcessOutput(0, "ok", false, 10));
        when(fixture.toolResults.available()).thenReturn(false);
        when(fixture.proofs.available()).thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> fixture.application.bash(
                new ControlledCodeCommands.Bash(
                        "project-1", "repo-1", "repair-1", "session-1", "run-1",
                        "mvn test", ControlledCodeEffect.TEST_OR_BUILD, "", 30_000,
                        "cp-1", 1, "package-hash", "HIGH"),
                "alice"));

        assertTrue(error.getMessage().contains("TRUSTED_PROOF_STORE_UNAVAILABLE"));
        verify(fixture.workspaces).recordTestOutcome("repair-1", "run:run-1", false);
    }

    @Test
    void dangerousBashIsRejectedBeforeExecutionAndReadOnlyRepositoryBashUsesRegisteredRoot() {
        Fixture fixture = fixture();
        assertThrows(SecurityException.class, () -> fixture.application.bash(
                new ControlledCodeCommands.Bash(
                        "project-1", "repo-1", "repair-1", "", "",
                        "git push origin main", ControlledCodeEffect.WRITE_REPAIR_WORKSPACE,
                        "", 30_000, "", 0, "", "MEDIUM"),
                "alice"));
        verify(fixture.files, never()).execute(any(), any(), anyInt(), anyInt());

        when(fixture.sources.find("project-1", "repo-1")).thenReturn(Optional.of(repository()));
        when(fixture.files.directory(Path.of("/tmp/repo"), "")).thenReturn(Path.of("/tmp/repo"));
        when(fixture.files.execute(
                eq(Path.of("/tmp/repo")), eq(List.of("pwd")), eq(30_000), eq(256 * 1024)))
                .thenReturn(new ControlledCodeFilePort.ProcessOutput(0, "/tmp/repo\n", false, 2));
        when(fixture.toolResults.available()).thenReturn(false);

        ControlledCodeResult.Bash result = fixture.application.bash(
                new ControlledCodeCommands.Bash(
                        "project-1", "repo-1", "", "", "",
                        "pwd", ControlledCodeEffect.READ_ONLY, "", 30_000,
                        "", 0, "", "LOW"),
                "alice");

        assertEquals(0, result.exitCode());
        verify(fixture.workspaces, never()).claimWriter(eq(""), any());
    }

    @Test
    void worktreeDiffAndCommitDelegateToTypedWorkspaceApplication() {
        Fixture fixture = fixture();
        when(fixture.workspaces.enterWorktree(
                "project-1", "service-1", "repo-1", "prod", BASE, "alice"))
                .thenReturn(workspace());
        when(fixture.workspaces.claimWriter("repair-1", "run:run-1"))
                .thenReturn(new RepairWriterLease(
                        "repair-1", "project-1", "run:run-1", "lease-1", 3, "later", 4));
        RepairDiffSnapshot diff = new RepairDiffSnapshot(
                "repair-1", BASE, REPAIR, List.of("src/App.java"),
                "1 file changed", "a".repeat(64), 100);
        RepairCommitResult commit = new RepairCommitResult(
                diff, REPAIR, RepairWorkspaceStatus.COMMITTED, "alice");
        when(fixture.workspaces.computeDiff("repair-1")).thenReturn(diff);
        when(fixture.workspaces.commitRepair("repair-1", "fix", "alice", "run:run-1"))
                .thenReturn(commit);

        ControlledCodeResult.Worktree entered = fixture.application.enterWorktree(
                new ControlledCodeCommands.EnterWorktree(
                        "project-1", "service-1", "repo-1", "prod", BASE, "run-1"),
                "alice");

        assertEquals(3, entered.writerFencingToken());
        assertEquals(diff, fixture.application.computeDiff("repair-1", "alice"));
        assertEquals(commit, fixture.application.commit(
                new ControlledCodeCommands.Commit("repair-1", "fix", "run-1"), "alice"));
    }

    private Fixture fixture() {
        ControlledCodeSourcePort sources = mock(ControlledCodeSourcePort.class);
        RepairWorkspaceApplicationService workspaces = mock(RepairWorkspaceApplicationService.class);
        ControlledCodeFilePort files = mock(ControlledCodeFilePort.class);
        ControlledCodeAuditPort audit = mock(ControlledCodeAuditPort.class);
        ControlledCodeToolResultPort toolResults = mock(ControlledCodeToolResultPort.class);
        ControlledCodeProofPort proofs = mock(ControlledCodeProofPort.class);
        when(workspaces.get("repair-1")).thenReturn(workspace());
        when(workspaces.find("repair-1")).thenReturn(Optional.of(workspace()));
        when(workspaces.worktreePath("repair-1")).thenReturn(Path.of("/tmp/repair-1"));
        return new Fixture(
                sources, workspaces, files, audit, toolResults, proofs,
                new ControlledCodeApplicationService(
                        sources, workspaces, files, audit, toolResults, proofs));
    }

    private RepairWorkspace workspace() {
        return new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE, REPAIR,
                RepairWorkspaceStatus.ACTIVE, "fix", "patch", List.of("src/App.java"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", "", "", 0L,
                "alice", "now", "now");
    }

    private SourceRepository repository() {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo",
                "HEAD", BASE, "READY", "alice", "now", "now");
    }

    private record Fixture(
            ControlledCodeSourcePort sources,
            RepairWorkspaceApplicationService workspaces,
            ControlledCodeFilePort files,
            ControlledCodeAuditPort audit,
            ControlledCodeToolResultPort toolResults,
            ControlledCodeProofPort proofs,
            ControlledCodeApplicationService application) {
    }
}
