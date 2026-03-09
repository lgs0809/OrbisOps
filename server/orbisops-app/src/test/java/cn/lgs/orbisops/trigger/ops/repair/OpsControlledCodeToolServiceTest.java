package cn.lgs.orbisops.trigger.ops.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeApplicationService;
import cn.lgs.orbisops.application.repair.ControlledCodeCommands;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.trigger.application.repair.OpsControlledCodeMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsControlledCodeToolServiceTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";

    @Test
    void compatibilityAclKeepsAllToolExecutionMapMethods() {
        ControlledCodeApplicationService application = mock(ControlledCodeApplicationService.class);
        OpsControlledCodeToolService facade =
                new OpsControlledCodeToolService(application, new OpsControlledCodeMapper());
        when(application.read(any(), eq("alice"))).thenReturn(new ControlledCodeResult.Read(
                "project-1", "repo-1", "", BASE, "App.java", "a".repeat(64), 1, 20, 1,
                List.of(new ControlledCodeResult.Line(1, "class App"))));
        when(application.grep(any(), eq("alice"))).thenReturn(new ControlledCodeResult.Search(List.of(
                new ControlledCodeResult.SearchHit("App.java", 1, "class App", "REGISTERED_REPO_GREP"))));
        when(application.glob(any(), eq("alice"))).thenReturn(new ControlledCodeResult.Glob(List.of(
                new ControlledCodeResult.FileEntry("App.java", 10, "now"))));
        when(application.edit(any(), eq("alice"))).thenReturn(new ControlledCodeResult.Mutation(
                "repair-1", "App.java", "b".repeat(64), "c".repeat(64),
                "d".repeat(64), "e".repeat(64), 1, false));
        when(application.write(any(), eq("alice"))).thenReturn(new ControlledCodeResult.Mutation(
                "repair-1", "New.java", "b".repeat(64), "c".repeat(64), "", "", 0, true));
        when(application.bash(any(), eq("alice"))).thenReturn(new ControlledCodeResult.Bash(
                "f".repeat(64), 0, "SUCCEEDED", "ok", false, 10, "/tmp",
                ControlledCodeEffect.READ_ONLY, "0".repeat(64), "", null, ""));
        when(application.enterWorktree(any(), eq("alice"))).thenReturn(new ControlledCodeResult.Worktree(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE, 3, "ACTIVE"));
        when(application.exitWorktree(any(), eq("alice"))).thenReturn(
                new ControlledCodeResult.Exit("repair-1", "EXITED", false));
        when(application.lsp(any(ControlledCodeCommands.Lsp.class), eq("alice"))).thenReturn(
                new ControlledCodeResult.Lsp("LSP_NOT_CONFIGURED", true));
        RepairDiffSnapshot diff = new RepairDiffSnapshot(
                "repair-1", BASE, REPAIR, List.of("App.java"),
                "1 file changed", "a".repeat(64), 100);
        when(application.computeDiff("repair-1", "alice")).thenReturn(diff);
        when(application.commit(any(), eq("alice"))).thenReturn(new RepairCommitResult(
                diff, REPAIR, RepairWorkspaceStatus.COMMITTED, "alice"));

        assertEquals("App.java", facade.read(Map.of("path", "App.java"), "alice").get("path"));
        assertEquals(1, facade.grep(Map.of("query", "App"), "alice").get("count"));
        assertEquals(1, facade.glob(Map.of("pattern", "**/*.java"), "alice").get("count"));
        assertEquals(1, facade.edit(Map.of("workspaceId", "repair-1"), "alice").get("replacements"));
        assertEquals(true, facade.write(Map.of("workspaceId", "repair-1"), "alice").get("created"));
        assertEquals(0, facade.bash(Map.of("command", "pwd"), "alice").get("exitCode"));
        assertEquals(3L, facade.enterWorktree(Map.of(), "alice").get("writerFencingToken"));
        assertEquals("EXITED", facade.exitWorktree(Map.of("workspaceId", "repair-1"), "alice").get("status"));
        assertTrue((Boolean) facade.lsp(Map.of("projectId", "project-1"), "alice").get("readOnly"));
        assertEquals("a".repeat(64), facade.computeRepairDiff(
                Map.of("workspaceId", "repair-1"), "alice").get("diffHash"));
        assertEquals("COMMITTED", facade.commitRepair(
                Map.of("workspaceId", "repair-1"), "alice").get("status"));

        verify(application).read(any(ControlledCodeCommands.Read.class), eq("alice"));
        verify(application).commit(any(ControlledCodeCommands.Commit.class), eq("alice"));
    }
}
