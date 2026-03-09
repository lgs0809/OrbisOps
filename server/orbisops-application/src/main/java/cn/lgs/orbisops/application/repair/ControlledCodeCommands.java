package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;

public final class ControlledCodeCommands {

    private ControlledCodeCommands() {
    }

    public record Read(
            String projectId,
            String repositoryId,
            String revision,
            String workspaceId,
            String path,
            int startLine,
            int limit) {
    }

    public record Grep(
            String projectId,
            String repositoryId,
            String revision,
            String workspaceId,
            String query,
            int limit,
            boolean regex,
            boolean caseSensitive,
            String glob) {
    }

    public record Glob(
            String projectId,
            String repositoryId,
            String workspaceId,
            String pattern,
            int limit) {
    }

    public record Edit(
            String workspaceId,
            String path,
            String oldString,
            String newString,
            boolean replaceAll,
            String runId) {
    }

    public record Write(
            String workspaceId,
            String path,
            String content,
            String runId) {
    }

    public record Bash(
            String projectId,
            String repositoryId,
            String workspaceId,
            String sessionId,
            String runId,
            String command,
            ControlledCodeEffect expectedEffect,
            String cwd,
            int timeoutMs,
            String packageId,
            int packageVersion,
            String packageHash,
            String riskLevel,
            boolean background,
            String action,
            String executionId,
            int limitBytes) {

        public Bash(
                String projectId,
                String repositoryId,
                String workspaceId,
                String sessionId,
                String runId,
                String command,
                ControlledCodeEffect expectedEffect,
                String cwd,
                int timeoutMs,
                String packageId,
                int packageVersion,
                String packageHash,
                String riskLevel) {
            this(projectId, repositoryId, workspaceId, sessionId, runId, command, expectedEffect,
                    cwd, timeoutMs, packageId, packageVersion, packageHash, riskLevel,
                    false, "run", "", 0);
        }
    }

    public record Lsp(
            String projectId,
            String repositoryId,
            String revision,
            String workspaceId,
            String action,
            String path,
            int line,
            int character,
            String query) {
    }

    public record EnterWorktree(
            String projectId,
            String serviceId,
            String repositoryId,
            String environment,
            String baseCommit,
            String runId) {
    }

    public record ExitWorktree(String workspaceId, String runId) {
    }

    public record Commit(String workspaceId, String message, String runId) {
    }
}
