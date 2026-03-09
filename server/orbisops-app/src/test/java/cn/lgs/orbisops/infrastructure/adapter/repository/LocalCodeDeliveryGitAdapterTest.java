package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.repair.model.CodeDeliveryBranch;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalCodeDeliveryGitAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void publishesVerifiedCommitToStableLocalReviewBranch() throws Exception {
        Path repository = createRepository(tempDir.resolve("repo"));
        String commit = run(repository, "git", "rev-parse", "HEAD").trim();
        LocalCodeDeliveryGitAdapter adapter = new LocalCodeDeliveryGitAdapter("origin", 30, 65536);
        RepairWorkspace workspace = new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", commit, commit,
                RepairWorkspaceStatus.VERIFIED, "fix", "patch", List.of("app.txt"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", "", "", 0L,
                "alice", "now", "now");

        CodeDeliveryBranch branch = adapter.publishBranch(
                workspace, repository, "ops-repair/service-1/repair-1", "review fix", false);

        assertEquals("ops-repair/service-1/repair-1", branch.branchName());
        assertEquals(commit, branch.commitSha());
        assertEquals("ops-repair/service-1/repair-1",
                run(repository, "git", "branch", "--show-current").trim());
    }

    private Path createRepository(Path path) throws Exception {
        Files.createDirectories(path);
        Files.writeString(path.resolve("app.txt"), "ok\n", StandardCharsets.UTF_8);
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
