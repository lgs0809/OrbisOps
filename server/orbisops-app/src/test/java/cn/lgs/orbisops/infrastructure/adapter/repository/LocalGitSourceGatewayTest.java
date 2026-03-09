package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGitSourceGatewayTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesCommitReadsExactFileAndSearchesLiteralText() throws Exception {
        Path repositoryPath = createRepository(tempDir.resolve("repo"));
        LocalGitSourceGateway gateway = new LocalGitSourceGateway(
                tempDir.toString(), "git", 5, 1024, 64 * 1024);
        String commit = gateway.resolveCommit(repositoryPath.toString(), "HEAD");
        SourceRepository repository = repository(repositoryPath, commit);

        SourceFile file = gateway.readFile(repository, commit, "src/app.txt");

        assertEquals("hello controlled ops\n", file.content());
        assertEquals(1, gateway.search(repository, commit, "controlled ops", 20).size());
        assertTrue(gateway.allowedRootsConfigured());
    }

    @Test
    void rejectsRepositoryOutsideRootsOversizedAndBinaryFiles() throws Exception {
        Path repositoryPath = createRepository(tempDir.resolve("repo"));
        Path otherRoot = Files.createDirectories(tempDir.resolve("other"));
        LocalGitSourceGateway outside = new LocalGitSourceGateway(
                otherRoot.toString(), "git", 5, 1024, 64 * 1024);
        assertThrows(IllegalArgumentException.class,
                () -> outside.resolveCommit(repositoryPath.toString(), "HEAD"));

        Files.writeString(repositoryPath.resolve("src/large.txt"), "x".repeat(2048), StandardCharsets.UTF_8);
        run(repositoryPath, "git", "add", "src/large.txt");
        run(repositoryPath, "git", "commit", "-m", "large");
        LocalGitSourceGateway small = new LocalGitSourceGateway(
                tempDir.toString(), "git", 5, 1024, 64 * 1024);
        String commit = small.resolveCommit(repositoryPath.toString(), "HEAD");
        assertThrows(IllegalArgumentException.class,
                () -> small.readFile(repository(repositoryPath, commit), commit, "src/large.txt"));
    }

    private SourceRepository repository(Path path, String commit) {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", path.toString(),
                "HEAD", commit, "READY", "admin", "now", "now");
    }

    private Path createRepository(Path path) throws Exception {
        Files.createDirectories(path.resolve("src"));
        run(path, "git", "init");
        run(path, "git", "config", "user.email", "test@example.com");
        run(path, "git", "config", "user.name", "Test");
        Files.writeString(path.resolve("src/app.txt"), "hello controlled ops\n", StandardCharsets.UTF_8);
        run(path, "git", "add", "src/app.txt");
        run(path, "git", "commit", "-m", "initial");
        return path.toRealPath();
    }

    private void run(Path path, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(path.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor() == 0, output);
    }
}
