package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.repair.CodeDeliveryProviderPort;
import cn.lgs.orbisops.application.repair.CodeDeliverySecretPort;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCiSnapshot;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryPullRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class GithubCodeDeliveryProviderAdapter implements CodeDeliveryProviderPort {

    private final CodeDeliverySecretPort secrets;
    private final boolean enabled;
    private final String repository;
    private final String apiUrl;
    private final String tokenRef;
    private final HttpClient httpClient;

    @org.springframework.beans.factory.annotation.Autowired
    public GithubCodeDeliveryProviderAdapter(
            CodeDeliverySecretPort secrets,
            @Value("${orbisops.repair.github.enabled:false}") boolean enabled,
            @Value("${orbisops.repair.github.repository:}") String repository,
            @Value("${orbisops.repair.github.api-url:https://api.github.com}") String apiUrl,
            @Value("${orbisops.repair.github.token-ref:}") String tokenRef) {
        this(secrets, enabled, repository, apiUrl, tokenRef,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    GithubCodeDeliveryProviderAdapter(
            CodeDeliverySecretPort secrets,
            boolean enabled,
            String repository,
            String apiUrl,
            String tokenRef,
            HttpClient httpClient) {
        if (secrets == null) throw new IllegalArgumentException("CODE_DELIVERY_SECRET_PORT_REQUIRED");
        if (httpClient == null) throw new IllegalArgumentException("CODE_DELIVERY_HTTP_CLIENT_REQUIRED");
        this.secrets = secrets;
        this.enabled = enabled;
        this.repository = value(repository);
        this.apiUrl = value(apiUrl).isBlank() ? "https://api.github.com" : stripTrailingSlash(apiUrl);
        this.tokenRef = value(tokenRef);
        this.httpClient = httpClient;
    }

    @Override
    public boolean configured() {
        if (!enabled || repository.isBlank() || tokenRef.isBlank()) return false;
        try {
            return StringUtils.hasText(secrets.resolve(tokenRef));
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public CodeDeliveryPullRequest openPullRequest(
            String branchName,
            String title,
            String baseBranch,
            String workspaceId) {
        requireConfigured();
        JSONObject response = request(
                "/repos/" + repository + "/pulls",
                "POST",
                JSON.toJSONString(Map.of(
                        "title", required(title, "CODE_DELIVERY_TITLE_REQUIRED"),
                        "head", required(branchName, "CODE_DELIVERY_BRANCH_REQUIRED"),
                        "base", required(baseBranch, "CODE_DELIVERY_BASE_BRANCH_REQUIRED"),
                        "body", "Generated from verified repair workspace `"
                                + required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED") + "`.")));
        return new CodeDeliveryPullRequest(response.getString("html_url"));
    }

    @Override
    public Optional<CodeDeliveryCiSnapshot> latestCi(String branchName) {
        requireConfigured();
        JSONObject response = request(
                "/repos/" + repository + "/actions/runs?branch="
                        + URLEncoder.encode(required(branchName, "CODE_DELIVERY_BRANCH_REQUIRED"),
                        StandardCharsets.UTF_8)
                        + "&per_page=1",
                "GET",
                null);
        JSONArray runs = response.getJSONArray("workflow_runs");
        if (runs == null || runs.isEmpty()) return Optional.empty();
        JSONObject run = runs.getJSONObject(0);
        String conclusion = value(run.getString("conclusion"));
        String status = conclusion.isBlank() ? value(run.getString("status")) : conclusion;
        if (status.isBlank()) status = "UNKNOWN";
        return Optional.of(new CodeDeliveryCiSnapshot(status, run.getString("html_url")));
    }

    private JSONObject request(String path, String method, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + secrets.resolve(tokenRef))
                    .header("X-GitHub-Api-Version", "2022-11-28");
            builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (body != null) builder.header("Content-Type", "application/json");
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "GitHub API HTTP " + response.statusCode() + ": " + response.body());
            }
            return JSON.parseObject(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("GitHub Provider 调用失败：" + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (!configured()) throw new IllegalStateException("GitHub PR Provider 未配置");
    }

    private String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String stripTrailingSlash(String value) {
        return value(value).replaceAll("/+$", "");
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
