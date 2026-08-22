package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.source.SourceGitPort;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class LocalGitSourceGateway implements SourceGitPort {

    private static final Pattern COMMIT_SHA = Pattern.compile("[a-fA-F0-9]{40}");

    private final String allowedLocalRoots;
    private final String gitBinary;
    private final int commandTimeoutSeconds;
    private final long maxFileBytes;
    private final long maxCommandOutputBytes;

    public LocalGitSourceGateway(
            @Value("${orbisops.source-repository.allowed-local-roots:}") String allowedLocalRoots,
            @Value("${orbisops.source-repository.git-binary:git}") String gitBinary,
            @Value("${orbisops.source-repository.command-timeout-seconds:8}") int commandTimeoutSeconds,
            @Value("${orbisops.source-repository.max-file-bytes:1048576}") long maxFileBytes,
            @Value("${orbisops.source-repository.max-command-output-bytes:2097152}") long maxCommandOutputBytes) {
        this.allowedLocalRoots = allowedLocalRoots;
        this.gitBinary = gitBinary;
        this.commandTimeoutSeconds = commandTimeoutSeconds;
        this.maxFileBytes = maxFileBytes;
        this.maxCommandOutputBytes = maxCommandOutputBytes;
    }

    @Override
    public boolean allowedRootsConfigured() {
        return !allowedRoots().isEmpty();
    }

    @Override
    public String resolveCommit(String localPath, String revision) {
        Path repositoryPath = validateRepositoryPath(localPath);
        GitResult result = runGit(repositoryPath,
                List.of("rev-parse", "--verify", revision + "^{commit}"), 128);
        String commitSha = result.stdout().trim();
        if (!COMMIT_SHA.matcher(commitSha).matches()) {
            throw new IllegalArgumentException("无法解析 Git Commit：" + revision);
        }
        return commitSha.toLowerCase(Locale.ROOT);
    }

    @Override
    public SourceFile readFile(SourceRepository repository, String revision, String path) {
        Path repositoryPath = validateRepositoryPath(repository.localPath());
        String commitSha = resolveCommit(repository.localPath(), revision);
        long size = parseLong(runGit(
                repositoryPath, List.of("cat-file", "-s", commitSha + ":" + path), 128).stdout().trim());
        long safeMax = Math.max(1024, maxFileBytes);
        if (size < 0 || size > safeMax) {
            throw new IllegalArgumentException("文件大小超过读取上限：" + safeMax);
        }
        GitResult result = runGit(repositoryPath, List.of("show", commitSha + ":" + path), safeMax + 1);
        if (result.stdout().indexOf('\0') >= 0) {
            throw new IllegalArgumentException("不支持读取二进制文件");
        }
        return new SourceFile(repository.repositoryId(), commitSha, path, size, result.stdout());
    }

    @Override
    public List<SourceSearchHit> search(
            SourceRepository repository,
            String revision,
            String query,
            int limit) {
        Path repositoryPath = validateRepositoryPath(repository.localPath());
        String commitSha = resolveCommit(repository.localPath(), revision);
        GitResult result = runGitAllowNoMatch(
                repositoryPath,
                List.of("grep", "-n", "-I", "-F", "-e", query, commitSha, "--"),
                Math.max(1024, maxCommandOutputBytes));
        List<SourceSearchHit> hits = new ArrayList<>();
        for (String line : result.stdout().split("\\R")) {
            if (!StringUtils.hasText(line) || hits.size() >= limit) continue;
            String prefix = commitSha + ":";
            String value = line.startsWith(prefix) ? line.substring(prefix.length()) : line;
            int first = value.indexOf(':');
            int second = first < 0 ? -1 : value.indexOf(':', first + 1);
            if (first < 1 || second < 0) continue;
            hits.add(new SourceSearchHit(
                    repository.repositoryId(),
                    commitSha,
                    value.substring(0, first),
                    integer(value.substring(first + 1, second)),
                    value.substring(second + 1)));
        }
        return List.copyOf(hits);
    }

    private Path validateRepositoryPath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) throw new IllegalArgumentException("localPath 不能为空");
        try {
            Path path = Path.of(rawPath.trim()).toRealPath();
            if (!Files.isDirectory(path) || !isAllowedRoot(path)) {
                throw new IllegalArgumentException("代码仓库路径不在允许的本地根目录中");
            }
            GitResult result = runGit(path, List.of("rev-parse", "--is-inside-work-tree"), 64);
            if (!"true".equalsIgnoreCase(result.stdout().trim())) {
                throw new IllegalArgumentException("localPath 不是 Git 工作区");
            }
            return path;
        } catch (IOException e) {
            throw new IllegalArgumentException("代码仓库路径不可访问", e);
        }
    }

    private boolean isAllowedRoot(Path path) {
        List<Path> roots = allowedRoots();
        return !roots.isEmpty() && roots.stream().anyMatch(path::startsWith);
    }

    private List<Path> allowedRoots() {
        if (!StringUtils.hasText(allowedLocalRoots)) return List.of();
        List<Path> roots = new ArrayList<>();
        for (String item : allowedLocalRoots.split(",")) {
            if (!StringUtils.hasText(item)) continue;
            try {
                roots.add(Path.of(item.trim()).toRealPath());
            } catch (IOException ignored) {
                // Invalid configured roots are ignored; an empty result fails closed.
            }
        }
        return List.copyOf(roots);
    }

    private GitResult runGit(Path repositoryPath, List<String> arguments, long outputLimit) {
        GitResult result = runGitInternal(repositoryPath, arguments, outputLimit);
        if (result.exitCode() != 0) {
            throw new IllegalArgumentException("Git 命令失败：" + concise(result.stderr()));
        }
        return result;
    }

    private GitResult runGitAllowNoMatch(Path repositoryPath, List<String> arguments, long outputLimit) {
        GitResult result = runGitInternal(repositoryPath, arguments, outputLimit);
        if (result.exitCode() != 0 && result.exitCode() != 1) {
            throw new IllegalArgumentException("Git 检索失败：" + concise(result.stderr()));
        }
        return result;
    }

    private GitResult runGitInternal(Path repositoryPath, List<String> arguments, long outputLimit) {
        List<String> command = new ArrayList<>();
        command.add(StringUtils.hasText(gitBinary) ? gitBinary.trim() : "git");
        command.add("-c");
        command.add("safe.directory=" + repositoryPath);
        command.add("-C");
        command.add(repositoryPath.toString());
        command.addAll(arguments);
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            CompletableFuture<String> stdout = readAsync(process.getInputStream(), Math.max(128, outputLimit));
            CompletableFuture<String> stderr = readAsync(
                    process.getErrorStream(), Math.min(Math.max(128, outputLimit), 64 * 1024));
            boolean completed = process.waitFor(Math.max(1, commandTimeoutSeconds), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException("Git 命令执行超时");
            }
            return new GitResult(process.exitValue(), joinOutput(stdout), joinOutput(stderr));
        } catch (IOException e) {
            throw new IllegalStateException("无法启动 Git 命令", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git 命令被中断", e);
        } finally {
            if (process != null) process.destroy();
        }
    }

    private CompletableFuture<String> readAsync(InputStream input, long limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return readLimited(input, limit);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    private String joinOutput(CompletableFuture<String> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("读取 Git 输出失败", cause);
        }
    }

    private String readLimited(InputStream input, long limit) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = source.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IllegalArgumentException("Git 输出超过限制：" + limit);
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return -1; }
    }

    private int integer(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

    private String concise(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", " ") : "unknown";
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private record GitResult(int exitCode, String stdout, String stderr) {
    }
}
