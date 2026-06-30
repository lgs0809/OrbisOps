package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.repair.RepairWorktreeCommand;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRepairWorkspaceExecutionAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void entersWorktreeComputesDiffCommitsAndCleans() throws Exception {
        Path repositoryPath = createRepository(tempDir.resolve("repo"));
        String base = run(repositoryPath, "git", "rev-parse", "HEAD").trim();
        LocalRepairWorkspaceExecutionAdapter adapter = adapter();
        adapter.initialize();
        ProjectService service = service();
        SourceRepository repository = source(repositoryPath, base);

        RepairWorkspace workspace = adapter.enterWorktree(new RepairWorktreeCommand(
                "repair-1", "project-1", "service-1", "prod", base, "alice", service, repository));
        Files.writeString(
                adapter.worktreePath("repair-1").resolve("module/app.txt"),
                "fixed\n",
                StandardCharsets.UTF_8);
        RepairDiffSnapshot diff = adapter.computeDiff(workspace);
        RepairCommitResult commit = adapter.commit(workspace, "fix repair-1", "alice");
        RepairCleanupResult cleanup = adapter.cleanup(workspace, repository, true);

        assertEquals(RepairWorkspaceStatus.ACTIVE, workspace.status());
        assertEquals(List.of("module/app.txt"), diff.changedFiles());
        assertEquals(40, commit.repairCommit().length());
        assertEquals(RepairWorkspaceStatus.COMMITTED, commit.status());
        assertTrue(cleanup.removed());
        assertFalse(Files.exists(adapter.worktreePath("repair-1")));
    }

    @Test
    void cleanupMissingWorktreeIsIdempotent() throws Exception {
        Path repositoryPath = createRepository(tempDir.resolve("repo"));
        String base = run(repositoryPath, "git", "rev-parse", "HEAD").trim();
        LocalRepairWorkspaceExecutionAdapter adapter = adapter();
        adapter.initialize();
        RepairWorkspace workspace = new RepairWorkspace(
                "missing", "project-1", "service-1", "repo-1", "prod", base, "",
                RepairWorkspaceStatus.FAILED, "fix", "", List.of(), "", "", null, "", "", "", 0L,
                "alice", "now", "now");

        RepairCleanupResult cleanup = adapter.cleanup(workspace, source(repositoryPath, base), true);

        assertEquals("NO_TEMP_RESOURCE", cleanup.status());
        assertFalse(cleanup.removed());
    }

    @Test
    void disabledRepairDoesNotInitializeWorkspaceDirectories() {
        Path worktrees = tempDir.resolve("disabled-worktrees");
        Path artifacts = tempDir.resolve("disabled-artifacts");
        LocalRepairWorkspaceExecutionAdapter adapter = new LocalRepairWorkspaceExecutionAdapter(
                false,
                worktrees.toString(),
                artifacts.toString(),
                30,
                64 * 1024,
                "host",
                "docker",
                "maven:test",
                "node:test",
                "make:test");

        adapter.initialize();

        assertFalse(Files.exists(worktrees));
        assertFalse(Files.exists(artifacts));
    }

    private LocalRepairWorkspaceExecutionAdapter adapter() {
        return new LocalRepairWorkspaceExecutionAdapter(
                true,
                tempDir.resolve("sandboxes").toString(),
                tempDir.resolve("artifacts").toString(),
                30,
                64 * 1024,
                "host",
                "docker",
                "maven:test",
                "node:test",
                "make:test");
    }

    private ProjectService service() {
        return new ProjectService(
                "service-1", "project-1", "service", "repo-1", "module", BuildProfile.MAKE_CI,
                "", "", "", List.of(), "READY", "now", "now");
    }

    private SourceRepository source(Path path, String base) {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", path.toString(),
                "HEAD", base, "READY", "alice", "now", "now");
    }

    private Path createRepository(Path path) throws Exception {
        Files.createDirectories(path.resolve("module"));
        Files.writeString(path.resolve("module/app.txt"), "broken\n", StandardCharsets.UTF_8);
        run(path, "git", "init");
        run(path, "git", "config", "user.email", "test@example.com");
        run(path, "git", "config", "user.name", "Test");
        run(path, "git", "add", ".");
        run(path, "git", "commit", "-m", "initial");
        return path.toRealPath();
    }

    private String run(Path cwd, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
