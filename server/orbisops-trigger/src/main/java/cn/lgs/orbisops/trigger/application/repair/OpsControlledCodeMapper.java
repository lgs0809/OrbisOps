package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeCommands;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsControlledCodeMapper {

    public ControlledCodeCommands.Read read(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.Read(
                text(data.get("projectId")), text(data.get("repositoryId")), text(data.get("revision")),
                text(data.get("workspaceId")), text(data.get("path")),
                integer(first(data.get("startLine"), data.get("offset")), 1),
                integer(first(data.get("limit"), data.get("lineLimit")), 200));
    }

    public ControlledCodeCommands.Grep grep(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.Grep(
                text(data.get("projectId")), text(data.get("repositoryId")), text(data.get("revision")),
                text(data.get("workspaceId")), text(data.get("query")), integer(data.get("limit"), 50),
                bool(data.get("regex")), bool(data.get("caseSensitive")), text(data.get("glob")));
    }

    public ControlledCodeCommands.Glob glob(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.Glob(
                text(data.get("projectId")), text(data.get("repositoryId")), text(data.get("workspaceId")),
                text(data.get("pattern")), integer(data.get("limit"), 100));
    }

    public ControlledCodeCommands.Edit edit(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.Edit(
                text(data.get("workspaceId")), text(data.get("path")), text(data.get("oldString")),
                textRaw(data.get("newString")), bool(data.get("replaceAll")), text(data.get("runId")));
    }

    public ControlledCodeCommands.Write write(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.Write(
                text(data.get("workspaceId")), text(data.get("path")),
                textRaw(data.get("content")), text(data.get("runId")));
    }

    public ControlledCodeCommands.Bash bash(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.Bash(
                text(data.get("projectId")), text(data.get("repositoryId")), text(data.get("workspaceId")),
                text(data.get("sessionId")), text(data.get("runId")), text(data.get("command")),
                ControlledCodeEffect.require(text(data.get("expectedEffect"))), text(data.get("cwd")),
                integer(data.get("timeoutMs"), 30_000), text(data.get("packageId")),
                integer(first(data.get("packageVersion"), data.get("version")), 0),
                text(data.get("packageHash")), defaultText(data.get("riskLevel"), "MEDIUM"),
                bool(data.get("background")), defaultText(data.get("action"), "run"),
                text(data.get("executionId")), integer(data.get("limitBytes"), 64 * 1024));
    }

    public ControlledCodeCommands.Lsp lsp(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.Lsp(
                text(data.get("projectId")), text(data.get("repositoryId")), text(data.get("revision")),
                text(data.get("workspaceId")), defaultText(data.get("action"), "symbols"),
                text(data.get("path")), integer(data.get("line"), 1),
                integer(data.get("character"), 1), text(data.get("query")));
    }

    public ControlledCodeCommands.EnterWorktree enterWorktree(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.EnterWorktree(
                text(data.get("projectId")), text(data.get("serviceId")), text(data.get("repositoryId")),
                defaultText(data.get("environment"), "dev"), text(data.get("baseCommit")), text(data.get("runId")));
    }

    public ControlledCodeCommands.ExitWorktree exitWorktree(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.ExitWorktree(
                text(data.get("workspaceId")), text(data.get("runId")));
    }

    public ControlledCodeCommands.Commit commit(Map<String, Object> request) {
        Map<String, Object> data = safe(request);
        return new ControlledCodeCommands.Commit(
                text(data.get("workspaceId")), text(data.get("message")), text(data.get("runId")));
    }

    public String workspaceId(Map<String, Object> request) {
        return text(safe(request).get("workspaceId"));
    }

    public String projectId(Map<String, Object> request) {
        return text(safe(request).get("projectId"));
    }

    public Map<String, Object> view(ControlledCodeResult.Read result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("startLine", result.startLine());
        data.put("limit", result.limit());
        data.put("totalLines", result.totalLines());
        data.put("lines", result.lines().stream()
                .map(line -> Map.<String, Object>of("line", line.line(), "text", line.text()))
                .toList());
        putText(data, "workspaceId", result.workspaceId());
        putText(data, "projectId", result.projectId());
        putText(data, "repositoryId", result.repositoryId());
        putText(data, "commitSha", result.commitSha());
        data.put("path", result.path());
        data.put("contentHash", result.contentHash());
        return data;
    }

    public Map<String, Object> view(ControlledCodeResult.Search result) {
        List<Map<String, Object>> hits = result.hits().stream().map(hit -> Map.<String, Object>of(
                "file", hit.file(), "line", hit.line(), "snippet", hit.snippet(),
                "matchReason", hit.matchReason())).toList();
        return Map.of("hits", hits, "count", hits.size());
    }

    public Map<String, Object> view(ControlledCodeResult.Glob result) {
        List<Map<String, Object>> files = result.files().stream().map(file -> Map.<String, Object>of(
                "path", file.path(), "sizeBytes", file.sizeBytes(), "modifiedAt", file.modifiedAt())).toList();
        return Map.of("files", files, "count", files.size());
    }

    public Map<String, Object> editView(ControlledCodeResult.Mutation result) {
        return Map.of(
                "workspaceId", result.workspaceId(),
                "path", result.path(),
                "beforeHash", result.beforeHash(),
                "afterHash", result.afterHash(),
                "oldSnippetHash", result.oldSnippetHash(),
                "newSnippetHash", result.newSnippetHash(),
                "replacements", result.replacements());
    }

    public Map<String, Object> writeView(ControlledCodeResult.Mutation result) {
        return Map.of(
                "workspaceId", result.workspaceId(),
                "path", result.path(),
                "beforeHash", result.beforeHash(),
                "afterHash", result.afterHash(),
                "created", result.created());
    }

    public Map<String, Object> view(ControlledCodeResult.Bash result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("commandHash", result.commandHash());
        data.put("exitCode", result.exitCode());
        data.put("status", result.status());
        data.put("stdoutPreview", result.stdoutPreview());
        data.put("outputTruncated", result.outputTruncated());
        data.put("durationMs", result.durationMs());
        data.put("cwd", result.cwd());
        data.put("expectedEffect", result.expectedEffect().name());
        data.put("outputHash", result.outputHash());
        putText(data, "resultId", result.resultId());
        if (result.storedTruncated() != null) data.put("truncated", result.storedTruncated());
        putText(data, "fullOutputRef", result.fullOutputRef());
        putText(data, "executionId", result.executionId());
        if (result.pid() != null) data.put("pid", result.pid());
        putText(data, "logRef", result.logRef());
        return data;
    }

    public Map<String, Object> view(ControlledCodeResult.Worktree result) {
        return Map.of(
                "workspaceId", result.workspaceId(),
                "projectId", result.projectId(),
                "serviceId", result.serviceId(),
                "repositoryId", result.repositoryId(),
                "environment", result.environment(),
                "baseCommit", result.baseCommit(),
                "writerFencingToken", result.writerFencingToken(),
                "status", result.status());
    }

    public Map<String, Object> view(ControlledCodeResult.Exit result) {
        return Map.of("workspaceId", result.workspaceId(), "status", result.status(), "deleted", result.deleted());
    }

    public Map<String, Object> view(ControlledCodeResult.Lsp result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", result.status());
        data.put("readOnly", result.readOnly());
        putText(data, "action", result.action());
        putText(data, "resultJson", result.resultJson());
        putText(data, "reasonCode", result.reasonCode());
        return data;
    }

    public Map<String, Object> view(RepairDiffSnapshot result) {
        return Map.of(
                "workspaceId", result.workspaceId(),
                "baseCommit", result.baseCommit(),
                "headCommit", result.currentHead(),
                "changedFiles", result.changedFiles(),
                "diffStat", result.diffSummary(),
                "diffHash", result.diffHash(),
                "diffBytes", result.diffBytes());
    }

    public Map<String, Object> view(RepairCommitResult result) {
        Map<String, Object> data = new LinkedHashMap<>(view(result.diff()));
        data.put("repairCommit", result.repairCommit());
        data.put("status", result.status().name());
        data.put("actor", result.createdBy());
        return data;
    }

    private Map<String, Object> safe(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }

    private Object first(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(text(value));
    }

    private String defaultText(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textRaw(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void putText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
