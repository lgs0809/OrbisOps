package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLocalHttpTransportTest {

    @Test
    void getAndPostMustApplyTimeoutContentTypeBudgetAndSecretMasking() {
        List<HttpRequest> requests = new ArrayList<>();
        OpsLocalHttpTransport transport = new OpsLocalHttpTransport(
                settings(1024),
                request -> {
                    requests.add(request);
                    return "token=secret password:abc jdbc:mysql://user:pwd@host/db";
                });

        String get = transport.get("http://localhost/get");
        String post = transport.postJson("http://localhost/post", "{}");

        assertEquals("GET", requests.get(0).method());
        assertEquals("POST", requests.get(1).method());
        assertEquals(2L, requests.get(0).timeout().orElseThrow().toSeconds());
        assertEquals("application/json", requests.get(1).headers()
                .firstValue("Content-Type").orElseThrow());
        assertFalse(get.contains("secret"));
        assertFalse(get.contains("password:abc"));
        assertFalse(get.contains("user:pwd@"));
        assertTrue(get.contains("token=***"));
        assertTrue(post.contains("password=***"));
    }

    @Test
    void responseMustBeTruncatedToConfiguredBudget() {
        OpsLocalHttpTransport transport = new OpsLocalHttpTransport(
                settings(1024),
                request -> "x".repeat(2000));

        assertEquals(1024, transport.get("http://localhost").length());
    }

    @Test
    void senderFailureMustKeepStableMethodSpecificError() {
        OpsLocalHttpTransport transport = new OpsLocalHttpTransport(
                settings(1024),
                request -> {
                    throw new IllegalStateException("connection down");
                });

        IllegalStateException get = assertThrows(
                IllegalStateException.class,
                () -> transport.get("http://localhost"));
        IllegalStateException post = assertThrows(
                IllegalStateException.class,
                () -> transport.postJson("http://localhost", "{}"));

        assertTrue(get.getMessage().contains("HTTP GET 调用失败：connection down"));
        assertTrue(post.getMessage().contains("HTTP POST 调用失败：connection down"));
    }

    private OpsLocalAdapterSettings settings(int maxResponseBytes) {
        return new OpsLocalAdapterSettings(
                "http://prom",
                "http://es",
                "index",
                "index",
                "./logs",
                "./",
                2,
                200,
                maxResponseBytes);
    }
}
