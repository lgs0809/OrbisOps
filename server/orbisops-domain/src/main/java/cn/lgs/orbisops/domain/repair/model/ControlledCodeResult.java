package cn.lgs.orbisops.domain.repair.model;

import java.util.List;

public sealed interface ControlledCodeResult permits
        ControlledCodeResult.Read,
        ControlledCodeResult.Search,
        ControlledCodeResult.Glob,
        ControlledCodeResult.Mutation,
        ControlledCodeResult.Bash,
        ControlledCodeResult.Worktree,
        ControlledCodeResult.Exit,
        ControlledCodeResult.Lsp {

    record Line(int line, String text) {
        public Line {
            if (line < 1) throw new IllegalArgumentException("CONTROLLED_CODE_LINE_INVALID");
            text = raw(text);
        }
    }

    record Read(
            String projectId,
            String repositoryId,
            String workspaceId,
            String commitSha,
            String path,
            String contentHash,
            int startLine,
            int limit,
            int totalLines,
            List<Line> lines) implements ControlledCodeResult {
        public Read {
            projectId = value(projectId);
            repositoryId = value(repositoryId);
            workspaceId = value(workspaceId);
            commitSha = commit(commitSha, true);
            path = required(path, "CONTROLLED_CODE_PATH_REQUIRED");
            contentHash = hash(contentHash, "CONTROLLED_CODE_HASH_INVALID", false);
            if (startLine < 1 || limit < 1 || totalLines < 0) {
                throw new IllegalArgumentException("CONTROLLED_CODE_PAGE_INVALID");
            }
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    record SearchHit(String file, int line, String snippet, String matchReason) {
        public SearchHit {
            file = required(file, "CONTROLLED_CODE_SEARCH_FILE_REQUIRED");
            if (line < 1) throw new IllegalArgumentException("CONTROLLED_CODE_SEARCH_LINE_INVALID");
            snippet = raw(snippet);
            matchReason = required(matchReason, "CONTROLLED_CODE_MATCH_REASON_REQUIRED");
        }
    }

    record Search(List<SearchHit> hits) implements ControlledCodeResult {
        public Search {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }
    }

    record FileEntry(String path, long sizeBytes, String modifiedAt) {
        public FileEntry {
            path = required(path, "CONTROLLED_CODE_PATH_REQUIRED");
            if (sizeBytes < 0) throw new IllegalArgumentException("CONTROLLED_CODE_FILE_SIZE_INVALID");
            modifiedAt = required(modifiedAt, "CONTROLLED_CODE_MODIFIED_AT_REQUIRED");
        }
    }

    record Glob(List<FileEntry> files) implements ControlledCodeResult {
        public Glob {
            files = files == null ? List.of() : List.copyOf(files);
        }
    }

    record Mutation(
            String workspaceId,
            String path,
            String beforeHash,
            String afterHash,
            String oldSnippetHash,
            String newSnippetHash,
            int replacements,
            boolean created) implements ControlledCodeResult {
        public Mutation {
            workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
            path = required(path, "CONTROLLED_CODE_PATH_REQUIRED");
            beforeHash = hash(beforeHash, "CONTROLLED_CODE_BEFORE_HASH_INVALID", false);
            afterHash = hash(afterHash, "CONTROLLED_CODE_AFTER_HASH_INVALID", false);
            oldSnippetHash = hash(oldSnippetHash, "CONTROLLED_CODE_OLD_SNIPPET_HASH_INVALID", true);
            newSnippetHash = hash(newSnippetHash, "CONTROLLED_CODE_NEW_SNIPPET_HASH_INVALID", true);
            if (replacements < 0) throw new IllegalArgumentException("CONTROLLED_CODE_REPLACEMENTS_INVALID");
        }
    }

    record Bash(
            String commandHash,
            int exitCode,
            String status,
            String stdoutPreview,
            boolean outputTruncated,
            long durationMs,
            String cwd,
            ControlledCodeEffect expectedEffect,
            String outputHash,
            String resultId,
            Boolean storedTruncated,
            String fullOutputRef,
            String executionId,
            Long pid,
            String logRef) implements ControlledCodeResult {
        public Bash {
            commandHash = hash(commandHash, "CONTROLLED_CODE_COMMAND_HASH_INVALID", false);
            status = required(status, "CONTROLLED_CODE_STATUS_REQUIRED");
            stdoutPreview = raw(stdoutPreview);
            if (durationMs < 0) throw new IllegalArgumentException("CONTROLLED_CODE_DURATION_INVALID");
            cwd = required(cwd, "CONTROLLED_CODE_CWD_REQUIRED");
            if (expectedEffect == null) throw new IllegalArgumentException("CONTROLLED_CODE_EFFECT_REQUIRED");
            outputHash = hash(outputHash, "CONTROLLED_CODE_OUTPUT_HASH_INVALID", false);
            resultId = value(resultId);
            fullOutputRef = value(fullOutputRef);
            executionId = value(executionId);
            logRef = value(logRef);
        }

        public Bash(
                String commandHash, int exitCode, String status, String stdoutPreview,
                boolean outputTruncated, long durationMs, String cwd, ControlledCodeEffect expectedEffect,
                String outputHash, String resultId, Boolean storedTruncated, String fullOutputRef) {
            this(commandHash, exitCode, status, stdoutPreview, outputTruncated, durationMs, cwd,
                    expectedEffect, outputHash, resultId, storedTruncated, fullOutputRef, "", null, "");
        }
    }

    record Worktree(
            String workspaceId,
            String projectId,
            String serviceId,
            String repositoryId,
            String environment,
            String baseCommit,
            long writerFencingToken,
            String status) implements ControlledCodeResult {
        public Worktree {
            workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
            projectId = required(projectId, "REPAIR_PROJECT_ID_REQUIRED");
            serviceId = required(serviceId, "REPAIR_SERVICE_ID_REQUIRED");
            repositoryId = required(repositoryId, "SOURCE_REPOSITORY_ID_REQUIRED");
            environment = required(environment, "REPAIR_ENVIRONMENT_REQUIRED");
            baseCommit = commit(baseCommit, false);
            if (writerFencingToken < 0) throw new IllegalArgumentException("REPAIR_FENCING_TOKEN_INVALID");
            status = required(status, "REPAIR_WORKSPACE_STATUS_REQUIRED");
        }
    }

    record Exit(String workspaceId, String status, boolean deleted) implements ControlledCodeResult {
        public Exit {
            workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
            status = required(status, "CONTROLLED_CODE_STATUS_REQUIRED");
        }
    }

    record Lsp(
            String status,
            boolean readOnly,
            String action,
            String resultJson,
            String reasonCode) implements ControlledCodeResult {
        public Lsp {
            status = required(status, "CONTROLLED_CODE_STATUS_REQUIRED");
            action = value(action);
            resultJson = raw(resultJson);
            reasonCode = value(reasonCode);
        }

        public Lsp(String status, boolean readOnly) {
            this(status, readOnly, "", "", "");
        }
    }

    private static String commit(String value, boolean optional) {
        String normalized = value(value).toLowerCase();
        if (optional && normalized.isBlank()) return "";
        if (!normalized.matches("[a-f0-9]{40}")) {
            throw new IllegalArgumentException("CONTROLLED_CODE_COMMIT_INVALID");
        }
        return normalized;
    }

    private static String hash(String value, String error, boolean optional) {
        String normalized = value(value).toLowerCase();
        if (optional && normalized.isBlank()) return "";
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String raw(String value) {
        return value == null ? "" : value;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
