package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeResult;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControlledCodeRemoteApplicationServiceTest {

    private static final String BASE = "a".repeat(40);
    private static final String BEFORE = "b".repeat(64);
    private static final String AFTER = "c".repeat(64);

    @Test
    void remoteReadAndEditUseMcpShaCasWithoutExternalHostPath() {
        Fixture fixture = fixture();
        when(fixture.remote.read(any(), any(), any(), eq("src/App.java")))
                .thenReturn(new ControlledCodeRemotePort.RemoteRead("class App {}\n", BEFORE));
        when(fixture.remote.edit(any(), any(), eq("src/App.java"), eq(BEFORE),
                eq("App"), eq("AppFixed"), eq(false)))
                .thenReturn(new ControlledCodeRemotePort.RemoteMutation(
                        "src/App.java", BEFORE, AFTER, 1, false));

        ControlledCodeResult.Read read = fixture.application.read(
                new ControlledCodeCommands.Read(
                        "project-1", "repo-1", BASE, "repair-1", "src/App.java", 1, 100),
                "alice");
        assertEquals(BEFORE, read.contentHash());

        ControlledCodeResult.Mutation mutation = fixture.application.edit(
                new ControlledCodeCommands.Edit(
                        "repair-1", "src/App.java", "App", "AppFixed", false, "run-1"),
                "alice");
        assertEquals(AFTER, mutation.afterHash());
        verify(fixture.workspaces, never()).worktreePath("repair-1");
        verify(fixture.workspaces).claimWriter("repair-1", "run:run-1");
        verify(fixture.workspaces).markDirty("repair-1", "run:run-1");
    }

    @Test
    void remoteEditFailsClosedWhenFileChangedAfterRead() {
        Fixture fixture = fixture();
        when(fixture.remote.read(any(), any(), any(), eq("src/App.java")))
                .thenReturn(new ControlledCodeRemotePort.RemoteRead("class App {}\n", BEFORE))
                .thenReturn(new ControlledCodeRemotePort.RemoteRead("class AppChanged {}\n", AFTER));

        fixture.application.read(new ControlledCodeCommands.Read(
                "project-1", "repo-1", BASE, "repair-1", "src/App.java", 1, 100), "alice");

        assertThrows(SecurityException.class, () -> fixture.application.edit(
                new ControlledCodeCommands.Edit(
                        "repair-1", "src/App.java", "App", "AppFixed", false, "run-1"),
                "alice"));
        verify(fixture.remote, never()).edit(any(), any(), any(), any(), any(), any(), eq(false));
    }

    @Test
    void remoteBackgroundBashReturnsOwnedExecutionWithoutPrematureTestPass() {
        Fixture fixture = fixture();
        when(fixture.remote.bash(any(), any(), eq("java -jar app.jar"),
                eq(ControlledCodeEffect.TEST_OR_BUILD), eq(""), eq(30_000), eq(true),
                eq("run"), eq(""), eq(64 * 1024)))
                .thenReturn(new ControlledCodeRemotePort.RemoteBash(
                        "d".repeat(64), 0, "RUNNING", "", "e".repeat(64), false,
                        5L, ".", "exec-1", 1234L, "workspace://repair-1/process/exec-1/log"));

        ControlledCodeResult.Bash result = fixture.application.bash(
                new ControlledCodeCommands.Bash(
                        "project-1", "repo-1", "repair-1", "session-1", "run-1",
                        "java -jar app.jar", ControlledCodeEffect.TEST_OR_BUILD, "", 30_000,
                        "", 0, "", "MEDIUM", true, "run", "", 64 * 1024),
                "alice");

        assertEquals("RUNNING", result.status());
        assertEquals("exec-1", result.executionId());
        assertEquals(1234L, result.pid());
        verify(fixture.workspaces, never()).recordTestOutcome("repair-1", "run:run-1", true);
    }

    @Test
    void localCompatibilityRejectsBackgroundRatherThanRunningSynchronously() {
        ControlledCodeSourcePort sources = mock(ControlledCodeSourcePort.class);
        RepairWorkspaceApplicationService workspaces = mock(RepairWorkspaceApplicationService.class);
        ControlledCodeFilePort files = mock(ControlledCodeFilePort.class);
        ControlledCodeApplicationService application = new ControlledCodeApplicationService(
                sources, workspaces, files, mock(ControlledCodeAuditPort.class),
                mock(ControlledCodeToolResultPort.class), mock(ControlledCodeProofPort.class));
        when(workspaces.get("repair-1")).thenReturn(workspace());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> application.bash(
                new ControlledCodeCommands.Bash(
                        "project-1", "repo-1", "repair-1", "", "run-1",
                        "mvn test", ControlledCodeEffect.TEST_OR_BUILD, "", 30_000,
                        "", 0, "", "MEDIUM", true, "run", "", 64 * 1024),
                "alice"));
        assertTrue(error.getMessage().contains("LOCAL_CODE_BASH_BACKGROUND_NOT_SUPPORTED"));
        verify(files, never()).execute(any(), any(), any(Integer.class), any(Integer.class));
    }

    private Fixture fixture() {
        ControlledCodeSourcePort sources = mock(ControlledCodeSourcePort.class);
        RepairWorkspaceApplicationService workspaces = mock(RepairWorkspaceApplicationService.class);
        ControlledCodeFilePort files = mock(ControlledCodeFilePort.class);
        ControlledCodeRemotePort remote = mock(ControlledCodeRemotePort.class);
        ControlledCodeAuditPort audit = mock(ControlledCodeAuditPort.class);
        ControlledCodeToolResultPort toolResults = mock(ControlledCodeToolResultPort.class);
        ControlledCodeProofPort proofs = mock(ControlledCodeProofPort.class);
        SourceRepository repository = repository();
        when(workspaces.get("repair-1")).thenReturn(workspace());
        when(workspaces.find("repair-1")).thenReturn(Optional.of(workspace()));
        when(sources.find("project-1", "repo-1")).thenReturn(Optional.of(repository));
        when(remote.supports(repository)).thenReturn(true);
        when(toolResults.available()).thenReturn(false);
        when(proofs.available()).thenReturn(false);
        ControlledCodeApplicationService application = new ControlledCodeApplicationService(
                sources, workspaces, files, remote, audit, toolResults, proofs);
        return new Fixture(workspaces, remote, application);
    }

    private SourceRepository repository() {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "",
                SourceRepositoryAccessMode.MCP, "code-mcp", "logical-repo",
                "prod", BASE, "READY", "alice", "now", "now");
    }

    private RepairWorkspace workspace() {
        return new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE, "",
                RepairWorkspaceStatus.ACTIVE, "fix", "", List.of(),
                "", "", null, "", "", "", 0L, "alice", "now", "now");
    }

    private record Fixture(
            RepairWorkspaceApplicationService workspaces,
            ControlledCodeRemotePort remote,
            ControlledCodeApplicationService application) {
    }
}
