package cn.lgs.orbisops.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledServiceControlMcpServerIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistentServerProvidesFixedToolsAndIdempotentRestartAcrossProcesses() throws Exception {
        Path stateFile = temporaryDirectory.resolve("service-control-state.json");
        String deadline = Instant.now().plusSeconds(300).toString();
        Map<String, Object> command = restart("execution-1", 1, deadline, "operator-1");

        Map<String, Object> first;
        Map<String, Object> second;
        try (McpProcess serverA = start(stateFile); McpProcess serverB = start(stateFile)) {
            Set<String> names = serverA.listTools().stream()
                    .map(item -> String.valueOf(item.get("name")))
                    .collect(Collectors.toSet());
            assertEquals(Set.of(
                    "get_service_status",
                    "restart_service_dry_run",
                    "restart_service",
                    "get_operation_receipt"), names);
            assertFalse(names.contains("execute_shell"));
            assertFalse(names.contains("execute_sql"));

            first = serverA.call("restart_service", command);
            second = serverB.call("restart_service", command);
        }

        assertEquals("SUCCEEDED", first.get("status"));
        assertEquals("SUCCEEDED", second.get("status"));
        assertEquals(first.get("receiptId"), second.get("receiptId"));
        assertEquals(2, number(first.get("currentVersion")));
        assertEquals(1, number(first.get("currentRestartCount")));
        assertTrue(Boolean.TRUE.equals(second.get("replayed")));

        try (McpProcess restarted = start(stateFile)) {
            Map<String, Object> state = restarted.call("get_service_status", stateQuery("operator-1"));
            assertEquals(2, number(state.get("version")));
            assertEquals(1, number(state.get("restartCount")));
            Map<String, Object> receipt = restarted.call("get_operation_receipt", receiptQuery("execution-1", "operator-1"));
            assertEquals(first.get("receiptId"), receipt.get("receiptId"));
            assertEquals(true, receipt.get("replayed"));
        }
    }

    @Test
    void committedRestartWithLostResponseIsRecoveredByReceiptWithoutSecondRestart() throws Exception {
        Path stateFile = temporaryDirectory.resolve("ambiguous-restart-state.json");
        String executionKey = "execution-ambiguous";
        Map<String, Object> command = restart(
                executionKey, 1, Instant.now().plusSeconds(300).toString(), "operator-1");

        try (McpProcess crashing = start(stateFile, Map.of(
                "SERVICE_CONTROL_TEST_MODE", "true",
                "SERVICE_CONTROL_TEST_POST_COMMIT_ACTION", "exit",
                "SERVICE_CONTROL_TEST_POST_COMMIT_MATCH", executionKey,
                "SERVICE_CONTROL_LOCK_STALE_MS", "50"))) {
            IllegalStateException lostResponse = assertThrows(
                    IllegalStateException.class,
                    () -> crashing.call("restart_service", command));
            assertTrue(lostResponse.getMessage().contains("MCP process exited"));
        }

        Thread.sleep(150L);
        Map<String, Object> receipt;
        try (McpProcess restarted = start(stateFile, Map.of("SERVICE_CONTROL_LOCK_STALE_MS", "50"))) {
            receipt = restarted.call("get_operation_receipt", receiptQuery(executionKey, "operator-1"));
            assertEquals("SUCCEEDED", receipt.get("status"));
            assertEquals(executionKey, receipt.get("executionKey"));
            assertTrue(String.valueOf(receipt.get("resultHash")).startsWith("sha256:"));

            Map<String, Object> state = restarted.call("get_service_status", stateQuery("operator-1"));
            assertEquals(2, number(state.get("version")));
            assertEquals(1, number(state.get("restartCount")));

            Map<String, Object> replay = restarted.call("restart_service", command);
            assertEquals(receipt.get("receiptId"), replay.get("receiptId"));
            assertEquals(true, replay.get("replayed"));
            Map<String, Object> stateAfterReplay = restarted.call("get_service_status", stateQuery("operator-1"));
            assertEquals(2, number(stateAfterReplay.get("version")));
            assertEquals(1, number(stateAfterReplay.get("restartCount")));
        }

        Map<String, Object> persisted = JSON.parseObject(
                Files.readString(stateFile), new TypeReference<Map<String, Object>>() { });
        assertEquals(1, number(persisted.get("schemaVersion")));
        assertTrue(String.valueOf(persisted.get("storeHash")).startsWith("sha256:"));
    }

    @Test
    void emergencyStopBlocksNewRestartButPreservesReadsReceiptsAndCompletedReplay() throws Exception {
        Path stateFile = temporaryDirectory.resolve("emergency-stop-state.json");
        String deadline = Instant.now().plusSeconds(300).toString();
        Map<String, Object> completedCommand = restart("execution-before-stop", 1, deadline, "operator-1");
        Map<String, Object> completed;
        try (McpProcess server = start(stateFile)) {
            completed = server.call("restart_service", completedCommand);
            assertEquals("SUCCEEDED", completed.get("status"));
        }

        try (McpProcess stopped = start(stateFile, Map.of("SERVICE_CONTROL_EMERGENCY_STOP", "true"))) {
            Map<String, Object> replay = stopped.call("restart_service", completedCommand);
            assertEquals(completed.get("receiptId"), replay.get("receiptId"));
            assertEquals(true, replay.get("replayed"));

            Map<String, Object> blocked = stopped.call(
                    "restart_service",
                    restart("execution-after-stop", 2, deadline, "operator-1"));
            assertEquals("BLOCKED", blocked.get("status"));
            assertEquals("SERVICE_CONTROL_EMERGENCY_STOP_ACTIVE", blocked.get("reasonCode"));

            Map<String, Object> state = stopped.call("get_service_status", stateQuery("operator-1"));
            assertEquals(2, number(state.get("version")));
            assertEquals(1, number(state.get("restartCount")));
            Map<String, Object> receipt = stopped.call(
                    "get_operation_receipt", receiptQuery("execution-before-stop", "operator-1"));
            assertEquals(completed.get("receiptId"), receipt.get("receiptId"));
        }
    }

    @Test
    void serverFailsClosedForExpiredDeadlineVersionDriftReceiptScopeAndUnknownTools() throws Exception {
        Path stateFile = temporaryDirectory.resolve("fail-closed-state.json");
        try (McpProcess server = start(stateFile, Map.of(
                "SERVICE_CONTROL_ALLOWED_SERVICES", "order-service,other-service"))) {
            Map<String, Object> expired = server.call(
                    "restart_service",
                    restart("execution-expired", 1, Instant.now().minusSeconds(1).toString(), "operator-1"));
            assertEquals("SERVICE_CONTROL_DEADLINE_EXPIRED", expired.get("reasonCode"));

            Map<String, Object> first = server.call(
                    "restart_service",
                    restart("execution-first", 1, Instant.now().plusSeconds(300).toString(), "operator-1"));
            assertEquals("SUCCEEDED", first.get("status"));
            Map<String, Object> drift = server.call(
                    "restart_service",
                    restart("execution-drift", 1, Instant.now().plusSeconds(300).toString(), "operator-1"));
            assertEquals("SERVICE_CONTROL_EXPECTED_VERSION_MISMATCH", drift.get("reasonCode"));

            Map<String, Object> crossActor = server.call(
                    "get_operation_receipt", receiptQuery("execution-first", "operator-2"));
            assertEquals("SERVICE_CONTROL_RECEIPT_SCOPE_MISMATCH", crossActor.get("reasonCode"));

            Map<String, Object> unknown = server.call("execute_shell", Map.of("command", "restart"));
            assertEquals("BLOCKED", unknown.get("status"));
            assertEquals("SERVICE_CONTROL_TOOL_NOT_ALLOWED", unknown.get("reasonCode"));
        }
    }

    private McpProcess start(Path stateFile) throws IOException {
        return start(stateFile, Map.of());
    }

    private McpProcess start(Path stateFile, Map<String, String> overrides) throws IOException {
        Path script = workspaceRoot().resolve("scripts/mcp/service-control-mcp-server.mjs");
        ProcessBuilder builder = new ProcessBuilder("node", script.toString());
        Map<String, String> environment = builder.environment();
        environment.remove("SERVICE_CONTROL_TEST_MODE");
        environment.remove("SERVICE_CONTROL_TEST_POST_COMMIT_ACTION");
        environment.remove("SERVICE_CONTROL_TEST_POST_COMMIT_MATCH");
        environment.remove("SERVICE_CONTROL_EMERGENCY_STOP");
        environment.put("SERVICE_CONTROL_STATE_FILE", stateFile.toString());
        environment.put("SERVICE_CONTROL_ALLOWED_SERVICES", "order-service");
        environment.put("SERVICE_CONTROL_LOCK_TIMEOUT_MS", "2000");
        environment.put("SERVICE_CONTROL_LOCK_STALE_MS", "5000");
        if (overrides != null) environment.putAll(overrides);
        return new McpProcess(builder.start());
    }

    private Map<String, Object> stateQuery(String actor) {
        return Map.of(
                "projectId", "demo-project",
                "service", "order-service",
                "actor", actor);
    }

    private Map<String, Object> receiptQuery(String executionKey, String actor) {
        return Map.of(
                "projectId", "demo-project",
                "executionKey", executionKey,
                "actor", actor);
    }

    private Map<String, Object> restart(String executionKey, int expectedVersion, String deadline, String actor) {
        return Map.of(
                "projectId", "demo-project",
                "service", "order-service",
                "expectedVersion", expectedVersion,
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
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        }

        private List<Map<String, Object>> listTools() throws Exception {
            Map<String, Object> response = request("tools/list", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            return tools;
        }

        private Map<String, Object> call(String name, Map<String, Object> arguments) throws Exception {
            Map<String, Object> response = request("tools/call", Map.of("name", name, "arguments", arguments));
            if (response.containsKey("error")) throw new IllegalStateException("MCP error: " + response.get("error"));
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            return JSON.parseObject(String.valueOf(content.get(0).get("text")), new TypeReference<Map<String, Object>>() { });
        }

        private synchronized Map<String, Object> request(String method, Map<String, Object> params) throws Exception {
            long id = ids.incrementAndGet();
            writer.write(JSON.toJSONString(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params)));
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
            if (line == null) throw new IllegalStateException("MCP process exited; stderr=" + stderr());
            Map<String, Object> response = JSON.parseObject(line, new TypeReference<Map<String, Object>>() { });
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
