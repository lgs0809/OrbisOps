package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeRemotePort;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import cn.lgs.orbisops.trigger.ops.code.OpsCodeWorkspaceMcpClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class McpControlledCodeRemoteAdapter implements ControlledCodeRemotePort {

    private final OpsCodeWorkspaceMcpClient client;

    public McpControlledCodeRemoteAdapter(OpsCodeWorkspaceMcpClient client) {
        if (client == null) throw new IllegalArgumentException("CODE_MCP_CLIENT_REQUIRED");
        this.client = client;
    }

    @Override
    public boolean supports(SourceRepository repository) {
        return repository != null && repository.accessMode() == SourceRepositoryAccessMode.MCP;
    }

    @Override
    public RemoteRead read(SourceRepository repository, RepairWorkspace workspace, String revision, String path) {
        Map<String, Object> args = identity(repository, workspace, revision);
        args.put("path", path);
        JSONObject result = client.invoke(repository.projectId(), repository.codeMcpId(), "code_read", args);
        return new RemoteRead(value(result.getString("content")), required(result.getString("sha256"), "CODE_MCP_READ_HASH_MISSING"));
    }

    @Override
    public List<RemoteSearchHit> search(
            SourceRepository repository,
            RepairWorkspace workspace,
            String revision,
            String query,
            int limit,
            boolean regex,
            boolean caseSensitive,
            String glob) {
        Map<String, Object> args = identity(repository, workspace, revision);
        args.put("mode", "text");
        args.put("query", query);
        args.put("limit", limit);
        args.put("regex", regex);
        args.put("caseSensitive", caseSensitive);
        if (!value(glob).isBlank()) args.put("glob", glob);
        JSONObject result = client.invoke(repository.projectId(), repository.codeMcpId(), "code_search", args);
        JSONArray hits = result.getJSONArray("hits");
        if (hits == null || hits.isEmpty()) return List.of();
        List<RemoteSearchHit> values = new ArrayList<>(hits.size());
        for (Object raw : hits) {
            JSONObject hit = raw instanceof JSONObject object ? object : JSON.parseObject(JSON.toJSONString(raw));
            values.add(new RemoteSearchHit(
                    required(hit.getString("file"), "CODE_MCP_SEARCH_PATH_MISSING"),
                    Math.max(1, hit.getIntValue("line")),
                    value(hit.getString("snippet"))));
        }
        return List.copyOf(values);
    }

    @Override
    public List<RemoteFileEntry> glob(
            SourceRepository repository,
            RepairWorkspace workspace,
            String revision,
            String pattern,
            int limit) {
        Map<String, Object> args = identity(repository, workspace, revision);
        args.put("mode", "glob");
        args.put("pattern", pattern);
        args.put("query", pattern);
        args.put("limit", limit);
        JSONObject result = client.invoke(repository.projectId(), repository.codeMcpId(), "code_search", args);
        JSONArray files = result.getJSONArray("files");
        if (files == null || files.isEmpty()) return List.of();
        List<RemoteFileEntry> values = new ArrayList<>(files.size());
        for (Object raw : files) {
            JSONObject file = raw instanceof JSONObject object ? object : JSON.parseObject(JSON.toJSONString(raw));
            values.add(new RemoteFileEntry(
                    required(file.getString("path"), "CODE_MCP_GLOB_PATH_MISSING"),
                    Math.max(0L, file.getLongValue("sizeBytes")),
                    required(file.getString("modifiedAt"), "CODE_MCP_GLOB_MODIFIED_AT_MISSING")));
        }
        return List.copyOf(values);
    }

    @Override
    public RemoteMutation edit(
            SourceRepository repository,
            RepairWorkspace workspace,
            String path,
            String expectedSha256,
            String oldString,
            String newString,
            boolean replaceAll) {
        Map<String, Object> args = workspaceIdentity(workspace);
        args.put("mode", "edit");
        args.put("path", path);
        args.put("expectedSha256", expectedSha256);
        args.put("oldString", oldString);
        args.put("newString", newString == null ? "" : newString);
        args.put("replaceAll", replaceAll);
        return mutation(path, client.invoke(repository.projectId(), repository.codeMcpId(), "code_apply_patch", args));
    }

    @Override
    public RemoteMutation write(
            SourceRepository repository,
            RepairWorkspace workspace,
            String path,
            String expectedSha256,
            String content) {
        Map<String, Object> args = workspaceIdentity(workspace);
        args.put("mode", "write");
        args.put("path", path);
        if (!value(expectedSha256).isBlank()) args.put("expectedSha256", expectedSha256);
        args.put("content", content == null ? "" : content);
        return mutation(path, client.invoke(repository.projectId(), repository.codeMcpId(), "code_apply_patch", args));
    }

    @Override
    public RemoteBash bash(
            SourceRepository repository,
            RepairWorkspace workspace,
            String command,
            ControlledCodeEffect effect,
            String cwd,
            int timeoutMs,
            boolean background,
            String action,
            String executionId,
            int limitBytes) {
        Map<String, Object> args = workspaceIdentity(workspace);
        String normalizedAction = value(action).isBlank() ? "run" : value(action).toLowerCase();
        args.put("action", normalizedAction);
        if ("run".equals(normalizedAction)) {
            args.put("command", command);
            args.put("expectedEffect", effect == null ? ControlledCodeEffect.READ_ONLY.name() : effect.name());
            args.put("cwd", value(cwd));
            args.put("timeoutMs", timeoutMs);
            args.put("background", background);
        } else {
            args.put("executionId", required(executionId, "CODE_MCP_EXECUTION_ID_REQUIRED"));
            if (limitBytes > 0) args.put("limitBytes", limitBytes);
        }
        JSONObject result = client.invoke(repository.projectId(), repository.codeMcpId(), "code_bash", args);
        return new RemoteBash(
                hashOrEmpty(result.getString("commandHash")),
                result.containsKey("exitCode") && result.get("exitCode") != null ? result.getIntValue("exitCode") : 0,
                required(result.getString("status"), "CODE_MCP_BASH_STATUS_MISSING"),
                first(result, "output", "stdout"),
                hashOrEmpty(result.getString("outputHash")),
                result.getBooleanValue("truncated"),
                Math.max(0L, result.getLongValue("durationMs")),
                value(result.getString("cwd")).isBlank() ? "." : result.getString("cwd"),
                value(result.getString("executionId")),
                result.containsKey("pid") && result.get("pid") != null ? result.getLong("pid") : null,
                value(result.getString("logRef")));
    }

    @Override
    public RemoteLsp lsp(
            SourceRepository repository,
            RepairWorkspace workspace,
            String revision,
            String action,
            String path,
            int line,
            int character,
            String query) {
        Map<String, Object> args = identity(repository, workspace, revision);
        args.put("action", value(action).isBlank() ? "symbols" : action);
        if (!value(path).isBlank()) args.put("path", path);
        if (line > 0) args.put("line", line);
        if (character > 0) args.put("character", character);
        if (!value(query).isBlank()) args.put("query", query);
        JSONObject result = client.invoke(repository.projectId(), repository.codeMcpId(), "code_lsp", args);
        return new RemoteLsp(
                required(result.getString("status"), "CODE_MCP_LSP_STATUS_MISSING"),
                !result.containsKey("readOnly") || result.getBooleanValue("readOnly"),
                value(result.getString("action")),
                result.get("results"),
                value(result.getString("reasonCode")));
    }

    private Map<String, Object> identity(SourceRepository repository, RepairWorkspace workspace, String revision) {
        if (workspace != null) return workspaceIdentity(workspace);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("repositoryId", repository.logicalRoot());
        args.put("revision", value(revision).isBlank() ? repository.defaultCommitSha() : revision);
        return args;
    }

    private Map<String, Object> workspaceIdentity(RepairWorkspace workspace) {
        if (workspace == null) throw new IllegalArgumentException("CODE_MCP_WORKSPACE_REQUIRED");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("workspaceId", workspace.workspaceId());
        return args;
    }

    private RemoteMutation mutation(String path, JSONObject result) {
        return new RemoteMutation(
                path,
                required(result.getString("beforeSha256"), "CODE_MCP_BEFORE_HASH_MISSING"),
                required(result.getString("afterSha256"), "CODE_MCP_AFTER_HASH_MISSING"),
                Math.max(0, result.getIntValue("replacements")),
                result.getBooleanValue("created"));
    }

    private String hashOrEmpty(String value) {
        String normalized = value(value).toLowerCase();
        return normalized.matches("[a-f0-9]{64}") ? normalized : "";
    }

    private String first(JSONObject value, String first, String second) {
        String current = value(value.getString(first));
        return current.isBlank() ? value(value.getString(second)) : current;
    }

    private String required(String value, String code) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalStateException(code);
        return normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
