package cn.lgs.orbisops.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadonlyMcpGuardIntegrationTest {

    @Test
    void mysqlServerExposesOnlyReadonlyToolsAndRejectsDangerousOrUnauthorizedQueriesBeforeConnection()
            throws Exception {
        try (McpProcess server = start(
                "scripts/mcp/mysql-readonly-mcp-server.mjs",
                Map.of(
                        "MYSQL_MCP_MODE", "native",
                        "MYSQL_HOST", "127.0.0.1",
                        "MYSQL_PORT", "1",
                        "MYSQL_USER", "readonly_agent",
                        "MYSQL_PASSWORD", "test-only-password",
                        "MYSQL_MCP_ALLOWED_DATABASES", "sales",
                        "MYSQL_MCP_ALLOWED_TABLES", "sales.orders,sales.customers",
                        "MYSQL_MCP_ALLOW_EXPLAIN_SELECT", "true"))) {
            Set<String> tools = server.listTools();
            assertEquals(Set.of(
                    "mysql_health",
                    "query_slow_log",
                    "query_statement_digest",
                    "show_table_indexes",
                    "explain_select"), tools);
            assertFalse(tools.contains("execute_sql"));
            assertFalse(tools.contains("update_row"));
            assertFalse(tools.contains("delete_row"));

            assertError(server.call("execute_write_sql", Map.of("sql", "UPDATE sales.orders SET x=1")),
                    "Unknown tool");
            assertError(server.call("explain_select", Map.of(
                            "database", "sales",
                            "sql", "UPDATE orders SET status='done'")),
                    "MYSQL_MCP_ONLY_SELECT_ALLOWED");
            assertError(server.call("explain_select", Map.of(
                            "database", "sales",
                            "sql", "SELECT * FROM orders FOR UPDATE")),
                    "MYSQL_MCP_UNSAFE_SELECT_FORBIDDEN");
            assertError(server.call("explain_select", Map.of(
                            "database", "sales",
                            "sql", "SELECT * FROM orders; DELETE FROM orders")),
                    "MYSQL_MCP_SINGLE_SELECT_REQUIRED");
            assertError(server.call("explain_select", Map.of(
                            "database", "admin",
                            "sql", "SELECT * FROM users")),
                    "MYSQL_MCP_DATABASE_FORBIDDEN");
            assertError(server.call("explain_select", Map.of(
                            "database", "sales",
                            "sql", "SELECT * FROM secrets")),
                    "MYSQL_MCP_TABLE_FORBIDDEN");
            assertError(server.call("show_table_indexes", Map.of(
                            "database", "sales",
                            "table", "secrets")),
                    "MYSQL_MCP_TABLE_FORBIDDEN");
            assertError(server.call("explain_select", Map.of(
                            "database", "sales",
                            "sql", "WITH x AS (SELECT * FROM orders) SELECT * FROM x")),
                    "MYSQL_MCP_ONLY_SELECT_ALLOWED");
        }
    }

    @Test
    void mysqlServerDefaultsFailClosedForPrivilegedAccountAndDisabledExplain() throws Exception {
        try (McpProcess server = start(
                "scripts/mcp/mysql-readonly-mcp-server.mjs",
                Map.of(
                        "MYSQL_MCP_MODE", "native",
                        "MYSQL_HOST", "127.0.0.1",
                        "MYSQL_PORT", "1",
                        "MYSQL_USER", "root",
                        "MYSQL_PASSWORD", "test-only-password",
                        "MYSQL_MCP_ALLOWED_DATABASES", "sales",
                        "MYSQL_MCP_ALLOWED_TABLES", "sales.orders"))) {
            assertError(server.call("explain_select", Map.of(
                            "database", "sales",
                            "sql", "SELECT * FROM orders")),
                    "MYSQL_MCP_EXPLAIN_DISABLED");
            assertError(server.call("mysql_health", Map.of()),
                    "MYSQL_MCP_PRIVILEGED_USER_FORBIDDEN");
        }
    }

    @Test
    void redisServerExposesOnlyReadonlyToolsAndRejectsWriteCommandsAndKeyEscapeBeforeConnection()
            throws Exception {
        try (McpProcess server = start(
                "scripts/mcp/redis-readonly-mcp-server.mjs",
                Map.of(
                        "REDIS_HOST", "127.0.0.1",
                        "REDIS_PORT", "1",
                        "REDIS_USERNAME", "readonly_agent",
                        "REDIS_PASSWORD", "test-only-password",
                        "REDIS_MCP_ALLOWED_KEY_PREFIXES", "project-1:",
                        "REDIS_MCP_MAX_KEYS", "10",
                        "REDIS_MCP_MAX_VALUE_BYTES", "1024"))) {
            Set<String> tools = server.listTools();
            assertEquals(Set.of(
                    "redis_health",
                    "redis_info",
                    "redis_scan",
                    "redis_get",
                    "redis_ttl"), tools);
            assertFalse(tools.contains("redis_set"));
            assertFalse(tools.contains("redis_del"));
            assertFalse(tools.contains("redis_eval"));

            for (String command : List.of(
                    "redis_set", "redis_del", "redis_eval", "redis_config", "redis_flushdb")) {
                assertError(server.call(command, Map.of()), "Unknown tool");
            }
            assertError(server.call("redis_get", Map.of("key", "project-2:secret")),
                    "REDIS_MCP_KEY_FORBIDDEN");
            assertError(server.call("redis_ttl", Map.of("key", "project-2:secret")),
                    "REDIS_MCP_KEY_FORBIDDEN");
            assertError(server.call("redis_scan", Map.of("pattern", "*")),
                    "REDIS_MCP_SCAN_PATTERN_FORBIDDEN");
            assertError(server.call("redis_scan", Map.of("pattern", "project-2:*")),
                    "REDIS_MCP_SCAN_PATTERN_FORBIDDEN");
        }
    }

    @Test
    void redisServerDefaultsFailClosedForDefaultUserAndMissingNamespaceAllowlist() throws Exception {
        try (McpProcess defaultUser = start(
                "scripts/mcp/redis-readonly-mcp-server.mjs",
                Map.of(
                        "REDIS_HOST", "127.0.0.1",
                        "REDIS_PORT", "1",
                        "REDIS_PASSWORD", "test-only-password",
                        "REDIS_MCP_ALLOWED_KEY_PREFIXES", "project-1:"))) {
            assertError(defaultUser.call("redis_health", Map.of()),
                    "REDIS_MCP_READONLY_USERNAME_REQUIRED");
        }
        try (McpProcess noNamespace = start(
                "scripts/mcp/redis-readonly-mcp-server.mjs",
                Map.of(
                        "REDIS_HOST", "127.0.0.1",
                        "REDIS_PORT", "1",
                        "REDIS_USERNAME", "readonly_agent",
                        "REDIS_PASSWORD", "test-only-password"))) {
            assertError(noNamespace.call("redis_get", Map.of("key", "project-1:key")),
                    "REDIS_MCP_KEY_PREFIX_ALLOWLIST_REQUIRED");
        }
    }

    private void assertError(Map<String, Object> response, String expectedText) {
        assertTrue(response.containsKey("error"), () -> "expected error response but got " + response);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertTrue(String.valueOf(error.get("message")).contains(expectedText),
                () -> "expected error containing " + expectedText + " but got " + error);
    }

    private McpProcess start(String relativeScript, Map<String, String> environment) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                "node",
                workspaceRoot().resolve(relativeScript).toString());
        builder.environment().putAll(environment);
        return new McpProcess(builder.start());
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
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
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

        private Set<String> listTools() throws Exception {
            Map<String, Object> response = request("tools/list", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            return tools.stream()
                    .map(item -> String.valueOf(item.get("name")))
                    .collect(Collectors.toSet());
        }

        private Map<String, Object> call(String name, Map<String, Object> arguments) throws Exception {
            return request("tools/call", Map.of("name", name, "arguments", arguments));
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
            Future<String> future = executor.submit(reader::readLine);
            String line;
            try {
                line = future.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException error) {
                future.cancel(true);
                throw new IllegalStateException("MCP response timeout; stderr=" + stderr(), error);
            }
            if (line == null) throw new IllegalStateException("MCP exited; stderr=" + stderr());
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
            } finally {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
                executor.shutdownNow();
                reader.close();
                errorReader.close();
            }
        }
    }
}
