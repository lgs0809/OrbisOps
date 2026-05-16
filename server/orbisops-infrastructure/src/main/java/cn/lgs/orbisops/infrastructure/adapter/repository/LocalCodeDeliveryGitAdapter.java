package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.repair.CodeDeliveryGitPort;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryBranch;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LocalCodeDeliveryGitAdapter implements CodeDeliveryGitPort {

    private final String remote;
    private final Duration timeout;
    private final int maxOutputBytes;

    public LocalCodeDeliveryGitAdapter(
            @Value("${orbisops.repair.github.remote:origin}") String remote,
            @Value("${orbisops.repair.delivery.command-timeout-seconds:120}") long timeoutSeconds,
            @Value("${orbisops.repair.delivery.max-output-bytes:1048576}") int maxOutputBytes) {
        this.remote = value(remote).isBlank() ? "origin" : value(remote);
        this.timeout = Duration.ofSeconds(Math.max(10L, timeoutSeconds));
        this.maxOutputBytes = Math.max(4096, maxOutputBytes);
    }

    @Override
    public CodeDeliveryBranch publishBranch(
            RepairWorkspace workspace,
            Path worktree,
            String branchName,
            String title,
            boolean pushRemote) {
        if (workspace == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_REQUIRED");
        if (worktree == null) throw new IllegalArgumentException("CODE_DELIVERY_WORKTREE_REQUIRED");
        String branch = required(branchName, "CODE_DELIVERY_BRANCH_REQUIRED");
        if (!workspace.verifiedCommit().isBlank()) {
            run(worktree, List.of("git", "checkout", "-B", branch, workspace.verifiedCommit()));
        } else {
            run(worktree, List.of("git", "checkout", "-B", branch));
            run(worktree, List.of("git", "config", "user.name", "orbisops"));
            run(worktree, List.of("git", "config", "user.email", "ops-agent@local"));
            run(worktree, List.of("git", "add", "--all"));
            run(worktree, List.of("git", "commit", "-m", required(title, "CODE_DELIVERY_TITLE_REQUIRED")));
        }
        String commit = run(worktree, List.of("git", "rev-parse", "HEAD")).trim();
        if (pushRemote) {
            run(worktree, List.of("git", "push", remote, "HEAD:refs/heads/" + branch));
        }
        return new CodeDeliveryBranch(branch, commit);
    }

    private String run(Path cwd, List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(cwd.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> copyBounded(process.getInputStream(), output),
                    "code-delivery-git-output");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                reader.join(2000);
                throw new IllegalStateException(String.join(" ", command) + " timed out");
            }
            reader.join(2000);
            String text = output.toString(StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(String.join(" ", command) + " failed: " + text);
            }
            return text;
        } catch (Exception e) {
            throw new IllegalStateException("代码交付命令失败：" + e.getMessage(), e);
        }
    }

    private void copyBounded(InputStream input, ByteArrayOutputStream output) {
        try (input) {
            byte[] buffer = new byte[4096];
            int remaining = maxOutputBytes;
            int read;
            while (remaining > 0 && (read = input.read(buffer, 0, Math.min(buffer.length, remaining))) >= 0) {
                output.write(buffer, 0, read);
                remaining -= read;
            }
            while (input.read(buffer) >= 0) {
                // Drain to prevent the child process from blocking after the visible output budget is exhausted.
            }
        } catch (Exception ignored) {
            // Process exit status remains authoritative.
        }
    }

    private String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
