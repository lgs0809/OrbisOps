package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.source.SourceGitPort;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import cn.lgs.orbisops.trigger.ops.code.OpsCodeWorkspaceMcpClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SourceRepository adapter for repositories whose execution root lives behind Code Workspace MCP. */
@Component
public final class McpSourceGitAdapter implements SourceGitPort {

    private final OpsCodeWorkspaceMcpClient client;

    public McpSourceGitAdapter(OpsCodeWorkspaceMcpClient client) {
        if (client == null) throw new IllegalArgumentException("CODE_MCP_CLIENT_REQUIRED");
        this.client = client;
    }

    @Override
    public boolean allowedRootsConfigured() {
        return true;
    }

    @Override
    public String resolveCommit(String localPath, String revision) {
        throw new IllegalStateException("MCP_CODE_REPOSITORY_TYPED_IDENTITY_REQUIRED");
    }

    @Override
    public String resolveCommit(SourceRepositoryCandidate repository, String revision) {
        JSONObject result = client.invoke(
                repository.projectId(), repository.codeMcpId(), "code_info",
                Map.of("repositoryId", repository.logicalRoot(), "revision", revision));
        return requiredCommit(result.getString("resolvedCommit"));
    }

    @Override
    public String resolveCommit(SourceRepository repository, String revision) {
        JSONObject result = client.invoke(
                repository.projectId(), repository.codeMcpId(), "code_info",
                Map.of("repositoryId", repository.logicalRoot(), "revision", revision));
        return requiredCommit(result.getString("resolvedCommit"));
    }

    @Override
    public SourceFile readFile(SourceRepository repository, String revision, String path) {
        JSONObject result = client.invoke(
                repository.projectId(), repository.codeMcpId(), "code_read",
                Map.of("repositoryId", repository.logicalRoot(), "revision", revision, "path", path));
        String commit = requiredCommit(firstText(result, "commit", revision));
        return new SourceFile(
                repository.repositoryId(), commit,
                required(result.getString("path"), "CODE_MCP_READ_PATH_MISSING"),
                Math.max(0L, result.getLongValue("sizeBytes")),
                result.getString("content"));
    }

    @Override
    public List<SourceSearchHit> search(
            SourceRepository repository, String revision, String query, int limit) {
        JSONObject result = client.invoke(
                repository.projectId(), repository.codeMcpId(), "code_search",
                Map.of(
                        "repositoryId", repository.logicalRoot(),
                        "revision", revision,
                        "query", query,
                        "limit", limit));
        String commit = requiredCommit(firstText(result, "commit", revision));
        JSONArray hits = result.getJSONArray("hits");
        if (hits == null || hits.isEmpty()) return List.of();
        List<SourceSearchHit> values = new ArrayList<>(hits.size());
        for (Object raw : hits) {
            JSONObject hit = raw instanceof JSONObject object ? object : JSON.parseObject(JSON.toJSONString(raw));
            values.add(new SourceSearchHit(
                    repository.repositoryId(), commit,
                    required(hit.getString("file"), "CODE_MCP_SEARCH_PATH_MISSING"),
                    Math.max(0, hit.getIntValue("line")), hit.getString("snippet")));
        }
        return List.copyOf(values);
    }

    private String firstText(JSONObject value, String key, String fallback) {
        String result = value == null ? "" : value.getString(key);
        return StringUtils.hasText(result) ? result.trim() : value(fallback);
    }

    private String requiredCommit(String value) {
        String commit = required(value, "CODE_MCP_COMMIT_MISSING").toLowerCase();
        if (!commit.matches("[a-f0-9]{40}")) throw new IllegalStateException("CODE_MCP_COMMIT_INVALID");
        return commit;
    }

    private String required(String value, String code) {
        String result = value(value);
        if (result.isBlank()) throw new IllegalStateException(code);
        return result;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
