package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeCommands;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsControlledCodeMapperTest {

    private final OpsControlledCodeMapper mapper = new OpsControlledCodeMapper();

    @Test
    void parsesAllLegacyRequestAliasesIntoTypedCommands() {
        ControlledCodeCommands.Read read = mapper.read(Map.of(
                "projectId", "project-1", "repositoryId", "repo-1", "path", "App.java",
                "offset", 3, "lineLimit", 20));
        ControlledCodeCommands.Bash bash = mapper.bash(Map.of(
                "projectId", "project-1", "repositoryId", "repo-1", "workspaceId", "repair-1",
                "command", "mvn test", "expectedEffect", "TEST_OR_BUILD",
                "version", 2, "packageId", "cp-1", "packageHash", "hash-1"));
        ControlledCodeCommands.EnterWorktree worktree = mapper.enterWorktree(Map.of(
                "projectId", "project-1", "serviceId", "service-1", "baseCommit", "abc"));

        assertEquals(3, read.startLine());
        assertEquals(20, read.limit());
        assertEquals(ControlledCodeEffect.TEST_OR_BUILD, bash.expectedEffect());
        assertEquals(2, bash.packageVersion());
        assertEquals("dev", worktree.environment());
        assertEquals(ControlledCodeEffect.READ_ONLY, mapper.bash(Map.of()).expectedEffect());
    }

    @Test
    void mapsTypedReadSearchGlobMutationAndBashToLegacyShape() {
        ControlledCodeResult.Read read = new ControlledCodeResult.Read(
                "project-1", "repo-1", "", "0123456789abcdef0123456789abcdef01234567",
                "App.java", "a".repeat(64), 1, 20, 1,
                List.of(new ControlledCodeResult.Line(1, "class App")));
        ControlledCodeResult.Search search = new ControlledCodeResult.Search(List.of(
                new ControlledCodeResult.SearchHit("App.java", 1, "class App", "REGISTERED_REPO_GREP")));
        ControlledCodeResult.Glob glob = new ControlledCodeResult.Glob(List.of(
                new ControlledCodeResult.FileEntry("App.java", 10L, "now")));
        ControlledCodeResult.Mutation edit = new ControlledCodeResult.Mutation(
                "repair-1", "App.java", "b".repeat(64), "c".repeat(64),
                "d".repeat(64), "e".repeat(64), 1, false);
        ControlledCodeResult.Bash bash = new ControlledCodeResult.Bash(
                "f".repeat(64), 0, "SUCCEEDED", "ok", false, 10L, "/tmp",
                ControlledCodeEffect.TEST_OR_BUILD, "0".repeat(64),
                "result-1", false, "db:result-1");

        assertEquals("App.java", mapper.view(read).get("path"));
        assertEquals(1, mapper.view(search).get("count"));
        assertEquals(1, mapper.view(glob).get("count"));
        assertEquals(1, mapper.editView(edit).get("replacements"));
        assertEquals("result-1", mapper.view(bash).get("resultId"));
        assertEquals("db:result-1", mapper.view(bash).get("fullOutputRef"));
    }

    @Test
    void mapsWorktreeExitLspDiffAndCommitContracts() {
        ControlledCodeResult.Worktree worktree = new ControlledCodeResult.Worktree(
                "repair-1", "project-1", "service-1", "repo-1", "prod",
                "0123456789abcdef0123456789abcdef01234567", 3, "ACTIVE");
        ControlledCodeResult.Exit exit = new ControlledCodeResult.Exit("repair-1", "EXITED", false);
        ControlledCodeResult.Lsp lsp = new ControlledCodeResult.Lsp("LSP_NOT_CONFIGURED", true);
        RepairDiffSnapshot diff = new RepairDiffSnapshot(
                "repair-1", "0123456789abcdef0123456789abcdef01234567",
                "89abcdef0123456789abcdef0123456789abcdef", List.of("App.java"),
                "1 file changed", "a".repeat(64), 100);
        RepairCommitResult commit = new RepairCommitResult(
                diff, "89abcdef0123456789abcdef0123456789abcdef",
                RepairWorkspaceStatus.COMMITTED, "alice");

        assertEquals(3L, mapper.view(worktree).get("writerFencingToken"));
        assertFalse((Boolean) mapper.view(exit).get("deleted"));
        assertTrue((Boolean) mapper.view(lsp).get("readOnly"));
        assertEquals("a".repeat(64), mapper.view(diff).get("diffHash"));
        assertEquals("COMMITTED", mapper.view(commit).get("status"));
    }
}
