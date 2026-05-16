package cn.lgs.orbisops.integration;

import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdCommand;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdPolicy;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdReceipt;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledAlertThresholdMcpServerIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistentServerProvidesFixedToolsMultiProcessIdempotencyReconciliationAndCompensation()
            throws Exception {
        Path stateFile = temporaryDirectory.resolve("alert-threshold-state.json");
        String deadline = Instant.now().plusSeconds(300).toString();
        Map<String, Object> update = mutation(
                "execution-1", "approval-1", 800, 12, 1000, deadline);

        Map<String, Object> first;
        Map<String, Object> second;
        try (McpProcess serverA = start(stateFile); McpProcess serverB = start(stateFile)) {
            Set<String> names = serverA.listTools().stream()
                    .map(item -> String.valueOf(item.get("name")))
                    .collect(Collectors.toSet());
            assertEquals(Set.of(
                    "get_alert_threshold",
                    "update_alert_threshold",
                    "get_operation_receipt",
                    "restore_alert_threshold"), names);
            assertFalse(names.contains("execute_sql"));
            assertFalse(names.contains("execute_shell"));
            assertFalse(names.contains("invoke_any_http"));

            CompletableFuture<Map<String, Object>> callA = CompletableFuture.supplyAsync(
                    () -> serverA.callUnchecked("update_alert_threshold", update));
            CompletableFuture<Map<String, Object>> callB = CompletableFuture.supplyAsync(
                    () -> serverB.callUnchecked("update_alert_threshold", update));
            first = callA.get(10, TimeUnit.SECONDS);
            second = callB.get(10, TimeUnit.SECONDS);
        }

        assertEquals("SUCCEEDED", first.get("status"));
        assertEquals("SUCCEEDED", second.get("status"));
        assertEquals(first.get("receiptId"), second.get("receiptId"));
        assertEquals(13, number(first.get("currentVersion")));
        assertEquals(13, number(second.get("currentVersion")));
        assertTrue(Boolean.TRUE.equals(first.get("replayed"))
                || Boolean.TRUE.equals(second.get("replayed")));
        assertTrue(Files.isRegularFile(stateFile));

        UpdateAlertThresholdCommand javaCommand = new UpdateAlertThresholdCommand(
                "project-1",
                "latency_p95",
                new BigDecimal("800"),
                12,
                new BigDecimal("1000"),
                "approval-1",
                "execution-1",
                Instant.parse(deadline),
                "operator-1");
        UpdateAlertThresholdReceipt.from(first).verify(
                javaCommand,
                UpdateAlertThresholdPolicy.TOOL_NAME);

        String receiptId = String.valueOf(first.get("receiptId"));
        try (McpProcess restarted = start(stateFile)) {
            Map<String, Object> receipt = restarted.call(
                    "get_operation_receipt", receiptQuery("execution-1"));
            assertEquals("SUCCEEDED", receipt.get("status"));
            assertEquals(receiptId, receipt.get("receiptId"));
            assertEquals(true, receipt.get("replayed"));

            Map<String, Object> current = restarted.call(
                    "get_alert_threshold",
                    stateQuery());
            assertEquals("SUCCEEDED", current.get("status"));
            assertEquals(1000, number(current.get("value")));
            assertEquals(13, number(current.get("version")));

            Map<String, Object> conflict = new LinkedHashMap<>(update);
            conflict.put("newValue", 1100);
            Map<String, Object> conflictResult = restarted.call(
                    "update_alert_threshold", conflict);
            assertEquals("BLOCKED", conflictResult.get("status"));
            assertEquals("ALERT_THRESHOLD_EXECUTION_KEY_CONFLICT",
                    conflictResult.get("reasonCode"));

            Map<String, Object> drift = restarted.call(
                    "update_alert_threshold",
                    mutation("execution-drift", "approval-1", 800, 12, 1100, deadline));
            assertEquals("BLOCKED", drift.get("status"));
            assertEquals("ALERT_THRESHOLD_EXPECTED_VALUE_MISMATCH", drift.get("reasonCode"));

            Map<String, Object> compensation = restarted.call(
                    "restore_alert_threshold",
                    mutation("execution-1:rollback", "approval-comp", 1000, 13, 800, deadline));
            assertEquals("SUCCEEDED", compensation.get("status"));
            assertEquals(800, number(compensation.get("currentValue")));
            assertEquals(14, number(compensation.get("currentVersion")));
            assertNotEquals(receiptId, compensation.get("receiptId"));

            Map<String, Object> repeatedCompensation = restarted.call(
                    "restore_alert_threshold",
                    mutation("execution-1:rollback", "approval-comp", 1000, 13, 800, deadline));
            assertEquals(compensation.get("receiptId"), repeatedCompensation.get("receiptId"));
            assertEquals(true, repeatedCompensation.get("replayed"));

            Map<String, Object> restored = restarted.call(
                    "get_alert_threshold",
                    stateQuery());
            assertEquals(800, number(restored.get("value")));
            assertEquals(14, number(restored.get("version")));
        }
    }

    @Test
    void completedExecutionRemainsReusableAfterOriginalDeadlineExpires() throws Exception {
        Path stateFile = temporaryDirectory.resolve("expired-replay-state.json");
        try (McpProcess server = start(stateFile)) {
            String deadline = Instant.now().plusSeconds(2).toString();
            Map<String, Object> command = mutation(
                    "execution-expiring", "approval-1", 800, 12, 1000, deadline);
            Map<String, Object> first = server.call("update_alert_threshold", command);
            assertEquals("SUCCEEDED", first.get("status"));
            Thread.sleep(2200L);
            Map<String, Object> replay = server.call("update_alert_threshold", command);
            assertEquals("SUCCEEDED", replay.get("status"));
            assertEquals(first.get("receiptId"), replay.get("receiptId"));
            assertEquals(true, replay.get("replayed"));
        }
    }

    @Test
    void serverFailsClosedForMissingAuthorityExpiredDeadlineAndUnknownTools() throws Exception {
        Path stateFile = temporaryDirectory.resolve("fail-closed-state.json");
        try (McpProcess server = start(stateFile)) {
            Map<String, Object> unauthorizedProject = server.call(
                    "update_alert_threshold",
                    mutation(
                            "execution-unauthorized",
                            "approval-1",
                            800,
                            12,
                            1000,
                            Instant.now().plusSeconds(300).toString(),
                            "project-2",
                            "operator-1"));
            assertEquals("BLOCKED", unauthorizedProject.get("status"));
            assertEquals("ALERT_THRESHOLD_PROJECT_FORBIDDEN",
                    unauthorizedProject.get("reasonCode"));

            Map<String, Object> unauthorizedApproval = server.call(
                    "update_alert_threshold",
                    mutation(
                            "execution-approval",
                            "approval-missing",
                            800,
                            12,
                            1000,
                            Instant.now().plusSeconds(300).toString()));
            assertEquals("ALERT_THRESHOLD_APPROVAL_GRANT_NOT_FOUND",
                    unauthorizedApproval.get("reasonCode"));

            Map<String, Object> expired = server.call(
                    "update_alert_threshold",
                    mutation(
                            "execution-expired",
                            "approval-1",
                            800,
                            12,
                            1000,
                            Instant.now().minusSeconds(1).toString()));
            assertEquals("ALERT_THRESHOLD_DEADLINE_EXPIRED", expired.get("reasonCode"));

            Map<String, Object> unknown = server.call("execute_sql", Map.of("sql", "DELETE FROM x"));
            assertEquals("BLOCKED", unknown.get("status"));
            assertEquals("ALERT_THRESHOLD_TOOL_NOT_ALLOWED", unknown.get("reasonCode"));
        }
    }

    @Test
    void committedMutationWithLostResponseIsRecoveredByReceiptWithoutReplay() throws Exception {
        Path stateFile = temporaryDirectory.resolve("ambiguous-outcome-state.json");
        String deadline = Instant.now().plusSeconds(300).toString();
        Map<String, Object> command = mutation(
                "execution-ambiguous", "approval-1", 800, 12, 1000, deadline);
        Map<String, String> crashAfterCommit = Map.of(
                "ALERT_THRESHOLD_TEST_MODE", "true",
                "ALERT_THRESHOLD_TEST_POST_COMMIT_ACTION", "exit",
                "ALERT_THRESHOLD_TEST_POST_COMMIT_EXECUTION_KEY", "execution-ambiguous",
                "ALERT_THRESHOLD_LOCK_STALE_MS", "50");

        try (McpProcess crashing = start(stateFile, crashAfterCommit)) {
            IllegalStateException lostResponse = assertThrows(
                    IllegalStateException.class,
                    () -> crashing.call("update_alert_threshold", command));
            assertTrue(lostResponse.getMessage().contains("MCP process exited"));
        }

        Thread.sleep(150L);
        try (McpProcess restarted = start(
                stateFile,
                Map.of("ALERT_THRESHOLD_LOCK_STALE_MS", "50"))) {
            Map<String, Object> receipt = restarted.call(
                    "get_operation_receipt",
                    receiptQuery("execution-ambiguous"));
            assertEquals("SUCCEEDED", receipt.get("status"));
            assertEquals("execution-ambiguous", receipt.get("executionKey"));
            assertEquals("operator-1", receipt.get("actor"));

            Map<String, Object> current = restarted.call(
                    "get_alert_threshold",
                    stateQuery());
            assertEquals(1000, number(current.get("value")));
            assertEquals(13, number(current.get("version")));

            Map<String, Object> replay = restarted.call(
                    "update_alert_threshold",
                    command);
            assertEquals(receipt.get("receiptId"), replay.get("receiptId"));
            assertEquals(true, replay.get("replayed"));
        }

        Map<String, Object> persisted = JSON.parseObject(
                Files.readString(stateFile),
                new TypeReference<Map<String, Object>>() {
                });
        assertEquals(2, number(persisted.get("schemaVersion")));
        assertTrue(String.valueOf(persisted.get("storeHash")).startsWith("sha256:"));
    }

    @Test
    void providerEmergencyStopBlocksNewMutationsButKeepsReadsAndCompletedReplayAvailable()
            throws Exception {
        Path stateFile = temporaryDirectory.resolve("emergency-stop-state.json");
        String deadline = Instant.now().plusSeconds(300).toString();
        Map<String, Object> completedCommand = mutation(
                "execution-before-stop", "approval-1", 800, 12, 1000, deadline);
        Map<String, Object> completed;
        try (McpProcess server = start(stateFile)) {
            completed = server.call("update_alert_threshold", completedCommand);
            assertEquals("SUCCEEDED", completed.get("status"));
        }

        try (McpProcess stopped = start(
                stateFile,
                Map.of("ALERT_THRESHOLD_EMERGENCY_STOP", "true"))) {
            Map<String, Object> replay = stopped.call(
                    "update_alert_threshold",
                    completedCommand);
            assertEquals(completed.get("receiptId"), replay.get("receiptId"));
            assertEquals(true, replay.get("replayed"));

            Map<String, Object> blocked = stopped.call(
                    "update_alert_threshold",
                    mutation("execution-after-stop", "approval-1", 1000, 13, 1100, deadline));
            assertEquals("BLOCKED", blocked.get("status"));
            assertEquals("ALERT_THRESHOLD_EMERGENCY_STOP_ACTIVE", blocked.get("reasonCode"));

            Map<String, Object> current = stopped.call("get_alert_threshold", stateQuery());
            assertEquals(1000, number(current.get("value")));
            Map<String, Object> receipt = stopped.call(
                    "get_operation_receipt",
                    receiptQuery("execution-before-stop"));
            assertEquals(completed.get("receiptId"), receipt.get("receiptId"));
        }
    }

    @Test
    void exactApprovalExpiryOperationScopeReceiptScopeAndStoreIntegrityFailClosed()
            throws Exception {
        String deadline = Instant.now().plusSeconds(300).toString();
        Path expiredFile = temporaryDirectory.resolve("expired-approval-state.json");
        try (McpProcess expired = start(
                expiredFile,
                Map.of(
                        "ALERT_THRESHOLD_APPROVAL_GRANTS_JSON",
                        approvalGrants(Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600))))) {
            Map<String, Object> blocked = expired.call(
                    "update_alert_threshold",
                    mutation("execution-expired-approval", "approval-1", 800, 12, 1000, deadline));
            assertEquals("ALERT_THRESHOLD_APPROVAL_EXPIRED", blocked.get("reasonCode"));
        }

        Path scopedFile = temporaryDirectory.resolve("scoped-approval-state.json");
        try (McpProcess scoped = start(scopedFile)) {
            Map<String, Object> wrongOperation = scoped.call(
                    "restore_alert_threshold",
                    mutation("execution-wrong-operation", "approval-1", 800, 12, 1000, deadline));
            assertEquals(
                    "ALERT_THRESHOLD_APPROVAL_OPERATION_FORBIDDEN",
                    wrongOperation.get("reasonCode"));
        }

        Path receiptScopeFile = temporaryDirectory.resolve("receipt-scope-state.json");
        try (McpProcess scoped = start(
                receiptScopeFile,
                Map.of("ALERT_THRESHOLD_ALLOWED_ACTORS", "operator-1,operator-2"))) {
            scoped.call(
                    "update_alert_threshold",
                    mutation("execution-scoped-receipt", "approval-1", 800, 12, 1000, deadline));
            Map<String, Object> crossActor = scoped.call(
                    "get_operation_receipt",
                    Map.of(
                            "executionKey", "execution-scoped-receipt",
                            "projectId", "project-1",
                            "actor", "operator-2"));
            assertEquals("ALERT_THRESHOLD_RECEIPT_SCOPE_MISMATCH", crossActor.get("reasonCode"));
        }

        Map<String, Object> persisted = JSON.parseObject(
                Files.readString(receiptScopeFile),
                new TypeReference<Map<String, Object>>() {
                });
        @SuppressWarnings("unchecked")
        Map<String, Object> states = (Map<String, Object>) persisted.get("states");
        @SuppressWarnings("unchecked")
        Map<String, Object> threshold = (Map<String, Object>) states.get("project-1::latency_p95");
        threshold.put("value", 9999);
        Files.writeString(receiptScopeFile, JSON.toJSONString(persisted), StandardCharsets.UTF_8);

        try (McpProcess corrupted = start(receiptScopeFile)) {
            IllegalStateException integrityFailure = assertThrows(
                    IllegalStateException.class,
                    () -> corrupted.call("get_alert_threshold", stateQuery()));
            assertTrue(integrityFailure.getMessage().contains("ALERT_THRESHOLD_STORE_INTEGRITY_INVALID"));
        }
    }

    @Test
    void staleLookingLockOwnedByLiveProcessIsNeverStolen() throws Exception {
        Path stateFile = temporaryDirectory.resolve("live-owner-lock-state.json");
        Path lockFile = Path.of(stateFile + ".lock");
        Files.writeString(
                lockFile,
                JSON.toJSONString(Map.of(
                        "pid", ProcessHandle.current().pid(),
                        "nonce", "active-java-test-owner",
                        "createdAt", Instant.now().minusSeconds(60).toString())),
                StandardCharsets.UTF_8);
        Files.setLastModifiedTime(lockFile, FileTime.from(Instant.now().minusSeconds(60)));

        try (McpProcess server = start(
                stateFile,
                Map.of(
                        "ALERT_THRESHOLD_LOCK_STALE_MS", "10",
                        "ALERT_THRESHOLD_LOCK_TIMEOUT_MS", "100"))) {
            Map<String, Object> result = server.call("get_alert_threshold", stateQuery());
            assertEquals("FAILED", result.get("status"));
            assertEquals("ALERT_THRESHOLD_STORE_LOCK_TIMEOUT", result.get("reasonCode"));
            assertTrue(Files.exists(lockFile));
        } finally {
            Files.deleteIfExists(lockFile);
        }
    }

    private McpProcess start(Path stateFile) throws IOException {
        return start(stateFile, Map.of());
    }

    private McpProcess start(Path stateFile, Map<String, String> overrides) throws IOException {
        Path script = workspaceRoot().resolve("scripts/mcp/update-alert-threshold-mcp-server.mjs");
        ProcessBuilder builder = new ProcessBuilder("node", script.toString());
        Map<String, String> environment = builder.environment();
        environment.remove("ALERT_THRESHOLD_TEST_MODE");
        environment.remove("ALERT_THRESHOLD_TEST_POST_COMMIT_ACTION");
        environment.remove("ALERT_THRESHOLD_TEST_POST_COMMIT_EXECUTION_KEY");
        environment.remove("ALERT_THRESHOLD_EMERGENCY_STOP");
        environment.put("ALERT_THRESHOLD_STATE_FILE", stateFile.toString());
        environment.put("ALERT_THRESHOLD_ALLOWED_PROJECTS", "project-1");
        environment.put("ALERT_THRESHOLD_ALLOWED_METRICS", "latency_p95");
        environment.put("ALERT_THRESHOLD_ALLOWED_ACTORS", "operator-1");
        environment.put(
                "ALERT_THRESHOLD_APPROVAL_GRANTS_JSON",
                approvalGrants(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(3600)));
        environment.put(
                "ALERT_THRESHOLD_INITIAL_STATE_JSON",
                "{\"project-1\":{\"latency_p95\":{\"value\":800,\"version\":12}}}");
        if (overrides != null) environment.putAll(overrides);
        return new McpProcess(builder.start());
    }

    private String approvalGrants(Instant updateExpiresAt, Instant restoreExpiresAt) {
        return JSON.toJSONString(Map.of(
                "approval-1", Map.of(
                        "projectId", "project-1",
                        "metric", "latency_p95",
                        "actor", "operator-1",
                        "operations", List.of("update_alert_threshold"),
                        "expiresAt", updateExpiresAt.toString()),
                "approval-comp", Map.of(
                        "projectId", "project-1",
                        "metric", "latency_p95",
                        "actor", "operator-1",
                        "operations", List.of("restore_alert_threshold"),
                        "expiresAt", restoreExpiresAt.toString())));
    }

    private Map<String, Object> stateQuery() {
        return Map.of(
                "projectId", "project-1",
                "metric", "latency_p95",
                "actor", "operator-1");
    }

    private Map<String, Object> receiptQuery(String executionKey) {
        return Map.of(
                "executionKey", executionKey,
                "projectId", "project-1",
                "actor", "operator-1");
    }

    private Map<String, Object> mutation(
            String executionKey,
            String approvalId,
            int expectedValue,
            int expectedVersion,
            int newValue,
            String deadline) {
        return mutation(
                executionKey,
                approvalId,
                expectedValue,
                expectedVersion,
                newValue,
                deadline,
                "project-1",
                "operator-1");
    }

    private Map<String, Object> mutation(
            String executionKey,
            String approvalId,
            int expectedValue,
            int expectedVersion,
            int newValue,
            String deadline,
            String projectId,
            String actor) {
        return Map.of(
                "projectId", projectId,
                "metric", "latency_p95",
                "expectedValue", expectedValue,
                "expectedVersion", expectedVersion,
                "newValue", newValue,
                "approvalId", approvalId,
                "executionKey", executionKey,
                "deadline", deadline,
                "actor", actor);
    }

    private int number(Object value) {
        return ((Number) value).intValue();
    }

    private Path workspaceRoot() {
        Path start = Path.of("").toAbsolutePath().normalize();
        for (Path current = start; current != null; current = current.getParent()) {
            if (Files.isDirectory(current.resolve("scripts/mcp"))) return current;
        }
        throw new IllegalStateException("Cannot locate OrbisOps repository root from " + start);
    }

    private static final class McpProcess implements AutoCloseable {
        private final Process process;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private final BufferedReader errorReader;
        private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();
        private final AtomicLong ids = new AtomicLong();

        private McpProcess(Process process) {
            this.process = process;
            this.writer = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            this.reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
            this.errorReader = new BufferedReader(new InputStreamReader(
                    process.getErrorStream(), StandardCharsets.UTF_8));
        }

        private List<Map<String, Object>> listTools() throws Exception {
            Map<String, Object> response = request("tools/list", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            return tools;
        }

        private Map<String, Object> call(String name, Map<String, Object> arguments)
                throws Exception {
            Map<String, Object> response = request(
                    "tools/call",
                    Map.of("name", name, "arguments", arguments));
            if (response.containsKey("error")) {
                throw new IllegalStateException("MCP error: " + response.get("error"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            return JSON.parseObject(
                    String.valueOf(content.get(0).get("text")),
                    new TypeReference<Map<String, Object>>() {
                    });
        }

        private Map<String, Object> callUnchecked(
                String name,
                Map<String, Object> arguments) {
            try {
                return call(name, arguments);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }

        private synchronized Map<String, Object> request(
                String method,
                Map<String, Object> params) throws Exception {
            long id = ids.incrementAndGet();
            writer.write(JSON.toJSONString(Map.of(
                    "jsonrpc", "2.0",
                    "id", id,
                    "method", method,
                    "params", params)));
            writer.newLine();
            writer.flush();
            Future<String> lineFuture = readExecutor.submit(reader::readLine);
            String line;
            try {
                line = lineFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException error) {
                lineFuture.cancel(true);
                throw new IllegalStateException("MCP response timed out; stderr=" + stderr(), error);
            }
            if (line == null) {
                throw new IllegalStateException("MCP process exited; stderr=" + stderr());
            }
            Map<String, Object> response = JSON.parseObject(
                    line,
                    new TypeReference<Map<String, Object>>() {
                    });
            assertEquals(id, ((Number) response.get("id")).longValue());
            return response;
        }

        private String stderr() {
            try {
                List<String> lines = new ArrayList<>();
                while (errorReader.ready()) lines.add(errorReader.readLine());
                return String.join(" | ", lines);
            } catch (IOException ignored) {
                return "";
            }
        }

        @Override
        public void close() throws Exception {
            try {
                writer.close();
            } catch (IOException ignored) {
                // Expected when deterministic fault injection terminates after commit.
            } finally {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
                readExecutor.shutdownNow();
                reader.close();
                errorReader.close();
            }
        }
    }
}
