package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.repair.CodeDeliverySecretPort;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCiSnapshot;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubCodeDeliveryProviderAdapterTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void opensPullRequestAndReadsLatestCiThroughFixedGithubContract() throws Exception {
        CodeDeliverySecretPort secrets = mock(CodeDeliverySecretPort.class);
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> pr = mock(HttpResponse.class);
        HttpResponse<String> ci = mock(HttpResponse.class);
        when(secrets.resolve("github-token")).thenReturn("secret");
        when(pr.statusCode()).thenReturn(201);
        when(pr.body()).thenReturn("{\"html_url\":\"https://github.test/pr/1\"}");
        when(ci.statusCode()).thenReturn(200);
        when(ci.body()).thenReturn("{\"workflow_runs\":[{\"conclusion\":\"success\","
                + "\"status\":\"completed\",\"html_url\":\"https://github.test/run/1\"}]}");
        doReturn(pr, ci).when(client).send(
                any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        GithubCodeDeliveryProviderAdapter adapter = new GithubCodeDeliveryProviderAdapter(
                secrets, true, "owner/repo", "https://api.github.test/", "github-token", client);

        assertTrue(adapter.configured());
        assertEquals("https://github.test/pr/1", adapter.openPullRequest(
                "ops-repair/service-1/repair-1", "review fix", "main", "repair-1").url());
        CodeDeliveryCiSnapshot snapshot = adapter.latestCi(
                "ops-repair/service-1/repair-1").orElseThrow();
        assertEquals("SUCCESS", snapshot.status());
        assertEquals("https://github.test/run/1", snapshot.url());
    }

    @Test
    void missingConfigurationOrSecretIsNotConfigured() {
        CodeDeliverySecretPort secrets = mock(CodeDeliverySecretPort.class);
        when(secrets.resolve("missing")).thenThrow(new IllegalStateException("missing"));

        assertFalse(new GithubCodeDeliveryProviderAdapter(
                secrets, false, "owner/repo", "https://api.github.test", "missing",
                mock(HttpClient.class)).configured());
        assertFalse(new GithubCodeDeliveryProviderAdapter(
                secrets, true, "", "https://api.github.test", "missing",
                mock(HttpClient.class)).configured());
        assertFalse(new GithubCodeDeliveryProviderAdapter(
                secrets, true, "owner/repo", "https://api.github.test", "missing",
                mock(HttpClient.class)).configured());
    }
}
