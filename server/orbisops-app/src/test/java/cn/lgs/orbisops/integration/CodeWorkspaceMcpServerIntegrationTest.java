package cn.lgs.orbisops.integration;

import cn.lgs.orbisops.trigger.ops.code.OpsCodeWorkspaceMcpClient;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpClientFactory;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpClientRegistry;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpRemoteClientAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpRemoteInvocationAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpRuntimeInvoker;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpTransportSecurityPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpTransportSecuritySettings;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeWorkspaceMcpServerIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void realGitWorkspaceSupportsPinnedReadRepairBackgroundCommitAndCleanup() throws Exception {
        Path repository = createRepository();
        Path stateRoot = temporaryDirectory.resolve("code-mcp-state");
        String baseCommit = git(repository, "rev-parse", "HEAD").trim();
        int port = freePort();

        try (McpProcess server = start(repository, stateRoot)) {
            Set<String> names = server.listTools().stream()
                    .map(item -> String.valueOf(item.get("name")))
                    .collect(Collectors.toSet());
            assertEquals(Set.of(
                    "code_info", "code_search", "code_read", "code_lsp", "code_enter_worktree",
                    "code_apply_patch", "code_bash", "code_diff", "code_commit", "code_cleanup"), names);

            Map<String, Object> info = server.call("code_info", Map.of(
                    "repositoryId", "fixture", "revision", "HEAD"));
            assertEquals(baseCommit, info.get("resolvedCommit"));
            assertFalse(info.containsKey("repositoryRoot"));

            Map<String, Object> readBase = server.call("code_read", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit, "path", "README.md"));
            assertEquals(baseCommit, readBase.get("commit"));
            assertEquals("fixture", readBase.get("repositoryId"));
            assertTrue(String.valueOf(readBase.get("sha256")).matches("[a-f0-9]{64}"));

            Map<String, Object> search = server.call("code_search", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit, "query", "version=1"));
            assertEquals(1, number(search.get("hitCount")));

            Map<String, Object> insensitive = server.call("code_search", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "query", "VERSION=[0-9]", "regex", true, "caseSensitive", false));
            assertEquals(1, number(insensitive.get("hitCount")));

            Map<String, Object> glob = server.call("code_search", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "mode", "glob", "pattern", "*.md", "limit", 20));
            assertEquals(1, number(glob.get("fileCount")));
            assertEquals("README.md", strings(glob.get("files")).isEmpty() ? "" :
                    String.valueOf(((Map<?, ?>) ((List<?>) glob.get("files")).get(0)).get("path")));

            Map<String, Object> unavailableLsp = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit, "action", "symbols", "query", "App"));
            assertEquals("UNAVAILABLE", unavailableLsp.get("status"));
            assertEquals(true, unavailableLsp.get("readOnly"));

            String workspaceId = "rw-integration-1";
            Map<String, Object> entered = server.call("code_enter_worktree", Map.of(
                    "repositoryId", "fixture", "baseCommit", baseCommit,
                    "workspaceId", workspaceId, "projectId", "p1", "serviceId", "svc"));
            assertEquals(baseCommit, entered.get("baseCommit"));
            assertEquals("ACTIVE", entered.get("status"));

            Map<String, Object> worktreeRead = server.call("code_read", Map.of(
                    "workspaceId", workspaceId, "path", "README.md"));
            String expectedHash = String.valueOf(worktreeRead.get("sha256"));

            IllegalStateException staleCas = assertThrows(IllegalStateException.class, () -> server.call(
                    "code_apply_patch", Map.of(
                            "workspaceId", workspaceId,
                            "mode", "edit",
                            "path", "README.md",
                            "expectedSha256", "0".repeat(64),
                            "oldString", "version=1",
                            "newString", "version=2")));
            assertTrue(staleCas.getMessage().contains("CAS mismatch"));

            Map<String, Object> edited = server.call("code_apply_patch", Map.of(
                    "workspaceId", workspaceId,
                    "mode", "edit",
                    "path", "README.md",
                    "expectedSha256", expectedHash,
                    "oldString", "version=1",
                    "newString", "version=2"));
            assertEquals("APPLIED", edited.get("status"));
            assertNotEquals(expectedHash, edited.get("afterSha256"));

            Map<String, Object> bash = server.call("code_bash", Map.of(
                    "workspaceId", workspaceId,
                    "command", "git diff -- README.md",
                    "expectedEffect", "READ_ONLY"));
            assertEquals("SUCCEEDED", bash.get("status"));
            assertTrue(String.valueOf(bash.get("outputHash")).matches("[a-f0-9]{64}"));

            Map<String, Object> build = server.call("code_bash", Map.of(
                    "workspaceId", workspaceId,
                    "command", "mvn -q -DskipTests package",
                    "expectedEffect", "TEST_OR_BUILD",
                    "timeoutMs", 60_000));
            assertEquals("SUCCEEDED", build.get("status"));

            Map<String, Object> background = server.call("code_bash", Map.of(
                    "workspaceId", workspaceId,
                    "command", "java -cp target/classes example.HealthApp " + port,
                    "expectedEffect", "TEST_OR_BUILD",
                    "background", true,
                    "timeoutMs", 20_000));
            String executionId = String.valueOf(background.get("executionId"));
            assertTrue(executionId.startsWith("exec-"));
            assertEquals("RUNNING", background.get("status"));
            waitForProcess(server, workspaceId, executionId);
            Thread.sleep(250L);

            Map<String, Object> health = server.call("code_bash", Map.of(
                    "workspaceId", workspaceId,
                    "command", "curl -fsS http://127.0.0.1:" + port + "/actuator/health",
                    "expectedEffect", "READ_ONLY"));
            assertEquals("SUCCEEDED", health.get("status"));
            assertEquals("UP", String.valueOf(health.get("output")).trim());

            Map<String, Object> stopped = server.call("code_bash", Map.of(
                    "workspaceId", workspaceId, "action", "stop", "executionId", executionId));
            assertTrue(Set.of("STOPPED", "SUCCEEDED", "FAILED").contains(String.valueOf(stopped.get("status"))));

            IllegalStateException push = assertThrows(IllegalStateException.class, () -> server.call(
                    "code_bash", Map.of(
                            "workspaceId", workspaceId,
                            "command", "git push origin HEAD",
                            "expectedEffect", "TEST_OR_BUILD")));
            assertTrue(push.getMessage().contains("git push is blocked"));

            Map<String, Object> diff = server.call("code_diff", Map.of("workspaceId", workspaceId));
            assertEquals(List.of("README.md"), strings(diff.get("changedFiles")));
            assertTrue(String.valueOf(diff.get("diffHash")).matches("[a-f0-9]{64}"));

            Map<String, Object> committed = server.call("code_commit", Map.of(
                    "workspaceId", workspaceId, "message", "test repair", "actor", "integration"));
            String repairCommit = String.valueOf(committed.get("repairCommit"));
            assertTrue(repairCommit.matches("[a-f0-9]{40}"));
            assertNotEquals(baseCommit, repairCommit);
            assertEquals(List.of("README.md"), strings(committed.get("changedFiles")));

            Map<String, Object> cleanupOwnedProcess = server.call("code_bash", Map.of(
                    "workspaceId", workspaceId,
                    "command", "java -cp target/classes example.HealthApp " + port,
                    "expectedEffect", "TEST_OR_BUILD",
                    "background", true,
                    "timeoutMs", 20_000));
            String cleanupExecutionId = String.valueOf(cleanupOwnedProcess.get("executionId"));
            waitForProcess(server, workspaceId, cleanupExecutionId);

            Map<String, Object> cleanup = server.call("code_cleanup", Map.of("workspaceId", workspaceId));
            assertEquals("CLEANED", cleanup.get("status"));
            assertEquals(true, cleanup.get("worktreeRemoved"));
            assertTrue(number(cleanup.get("stoppedExecutions")) >= 1);
            Map<String, Object> cleanupAgain = server.call("code_cleanup", Map.of("workspaceId", workspaceId));
            assertEquals("NO_TEMP_RESOURCE", cleanupAgain.get("status"));
        }

        assertEquals(baseCommit, git(repository, "rev-parse", "HEAD").trim(),
                "base repository HEAD must never be modified by the repair server");
        assertFalse(Files.exists(stateRoot.resolve("worktrees/rw-integration-1")));
    }

    @Test
    void baseRepositoryRejectsSensitivePathsAndWorktreeRejectsEscapes() throws Exception {
        Path repository = createRepository();
        Files.writeString(repository.resolve(".env"), "TOKEN=do-not-read\n");
        String baseCommit = git(repository, "rev-parse", "HEAD").trim();
        try (McpProcess server = start(repository, temporaryDirectory.resolve("state-security"))) {
            IllegalStateException secret = assertThrows(IllegalStateException.class, () -> server.call(
                    "code_read", Map.of("repositoryId", "fixture", "revision", baseCommit, "path", ".env")));
            assertTrue(secret.getMessage().contains("sensitive file is blocked"));

            server.call("code_enter_worktree", Map.of(
                    "repositoryId", "fixture", "baseCommit", baseCommit,
                    "workspaceId", "rw-security", "projectId", "p1", "serviceId", "svc"));
            IllegalStateException escape = assertThrows(IllegalStateException.class, () -> server.call(
                    "code_read", Map.of("workspaceId", "rw-security", "path", "../README.md")));
            assertTrue(escape.getMessage().contains("path is invalid"));

            String sensitivePatch = """
                    diff --git a/id_rsa b/id_rsa
                    new file mode 100644
                    --- /dev/null
                    +++ b/id_rsa
                    @@ -0,0 +1 @@
                    +blocked-content
                    """;
            IllegalStateException blockedPatch = assertThrows(IllegalStateException.class, () -> server.call(
                    "code_apply_patch", Map.of(
                            "workspaceId", "rw-security", "mode", "patch", "unifiedDiff", sensitivePatch)));
            assertTrue(blockedPatch.getMessage().contains("sensitive file is blocked"));
            Map<String, Object> status = server.call("code_bash", Map.of(
                    "workspaceId", "rw-security", "command", "git status --short", "expectedEffect", "READ_ONLY"));
            assertFalse(String.valueOf(status.get("output")).contains("id_rsa"),
                    "blocked sensitive patch must not mutate the repair worktree before failing");
            server.call("code_cleanup", Map.of("workspaceId", "rw-security"));
        }
    }

    @Test
    void orbisOpsRuntimeDiscoversAndInvokesRealCodeWorkspaceMcpOverStdio() throws Exception {
        Path repository = createRepository();
        Path stateRoot = temporaryDirectory.resolve("runtime-mcp-state");
        String baseCommit = git(repository, "rev-parse", "HEAD").trim();
        Path script = workspaceRoot().resolve("scripts/mcp/code-workspace-mcp-server.mjs");

        OpsSecretResolver secrets = new OpsSecretResolver(new StandardEnvironment());
        OpsMcpTransportSecuritySettings securitySettings = OpsMcpTransportSecuritySettings.fromRaw(
                true,
                "stdio",
                "node",
                "",
                "CODE_WORKSPACE_REPOSITORY_ROOT,CODE_WORKSPACE_REPOSITORY_ID,CODE_WORKSPACE_STATE_ROOT,CODE_WORKSPACE_TIMEOUT_MS,CODE_WORKSPACE_BACKGROUND_TTL_MS",
                "",
                30);
        OpsMcpClientFactory factory = new OpsMcpClientFactory(
                secrets, new OpsMcpTransportSecurityPolicy(securitySettings));
        OpsMcpClientRegistry registry = new OpsMcpClientRegistry();
        OpsMcpRemoteClientAdapter remoteClients = new OpsMcpRemoteClientAdapter(registry, factory);
        OpsMcpRemoteInvocationAdapter remoteInvocations = new OpsMcpRemoteInvocationAdapter(remoteClients, () -> null);
        OpsMcpRuntimeInvoker runtime = new OpsMcpRuntimeInvoker(remoteClients, remoteInvocations);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("code-workspace-runtime-it")
                .projectId("project-runtime")
                .mcpId("code-mcp")
                .transport("stdio")
                .command("node")
                .args(List.of(script.toString()))
                .env(Map.of(
                        "CODE_WORKSPACE_REPOSITORY_ROOT", repository.toString(),
                        "CODE_WORKSPACE_REPOSITORY_ID", "fixture",
                        "CODE_WORKSPACE_STATE_ROOT", stateRoot.toString(),
                        "CODE_WORKSPACE_TIMEOUT_MS", "10000",
                        "CODE_WORKSPACE_BACKGROUND_TTL_MS", "20000"))
                .timeoutSeconds(15)
                .build();

        OpsProjectMcpRuntimeConfigService configs = org.mockito.Mockito.mock(OpsProjectMcpRuntimeConfigService.class);
        org.mockito.Mockito.when(configs.resolve("project-runtime", "code-mcp")).thenReturn(Optional.of(config));
        OpsCodeWorkspaceMcpClient client = new OpsCodeWorkspaceMcpClient(configs, runtime);
        try {
            Set<String> tools = runtime.inspectDefinitions(config).stream()
                    .map(item -> String.valueOf(item.get("toolName")))
                    .collect(Collectors.toSet());
            assertEquals(Set.of(
                    "code_info", "code_search", "code_read", "code_lsp", "code_enter_worktree",
                    "code_apply_patch", "code_bash", "code_diff", "code_commit", "code_cleanup"), tools);

            JSONObject info = client.invoke("project-runtime", "code-mcp", "code_info", Map.of(
                    "repositoryId", "fixture", "revision", "HEAD"));
            assertEquals(baseCommit, info.getString("resolvedCommit"));
            assertEquals("fixture", info.getString("repositoryId"));

            JSONObject read = client.invoke("project-runtime", "code-mcp", "code_read", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit, "path", "README.md"));
            assertEquals(baseCommit, read.getString("commit"));
            assertTrue(read.getString("sha256").matches("[a-f0-9]{64}"));
        } finally {
            runtime.invalidateAll();
        }
    }

    @Test
    void realJdtlsSupportsReadOnlySemanticsAndRefreshesDiagnosticsAfterRepairEdit() throws Exception {
        String command = System.getenv("CODE_WORKSPACE_REAL_JDTLS_COMMAND");
        String argsJson = System.getenv("CODE_WORKSPACE_REAL_JDTLS_ARGS_JSON");
        boolean managed = Boolean.parseBoolean(System.getenv("CODE_WORKSPACE_REAL_MANAGED_JDTLS"));
        boolean explicit = command != null && !command.isBlank() && argsJson != null && !argsJson.isBlank();
        assumeTrue(managed || explicit,
                "real JDT LS acceptance requires managed mode or an explicitly configured JDT LS executable");

        Path repository = createRepository();
        Path stateRoot = temporaryDirectory.resolve("jdtls-state");
        String baseCommit = git(repository, "rev-parse", "HEAD").trim();
        try (McpProcess server = managed
                ? startManagedJdtls(repository, stateRoot)
                : startWithJdtls(repository, stateRoot, command, argsJson)) {
            Map<String, Object> info = server.call("code_info", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit));
            assertEquals(true, ((Map<?, ?>) info.get("capabilities")).get("lsp"));

            Map<String, Object> symbols = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "symbols", "query", "Greeter"));
            assertEquals("SUCCEEDED", symbols.get("status"), symbols + "; serverStderr=" + server.stderr());
            assertFalse(((List<?>) symbols.get("results")).isEmpty());

            if (managed) {
                Map<String, Object> afterProvision = server.call("code_info", Map.of(
                        "repositoryId", "fixture", "revision", baseCommit));
                Map<?, ?> lsp = (Map<?, ?>) afterProvision.get("lsp");
                Map<?, ?> languages = (Map<?, ?>) lsp.get("languages");
                Map<?, ?> java = (Map<?, ?>) languages.get("java");
                assertEquals("MANAGED", java.get("mode"));
                assertEquals("1.43.0", java.get("version"));
                assertEquals(true, java.get("provisioned"));
                assertTrue(Files.isDirectory(stateRoot.resolve("runtimes/jdtls/1.43.0")));
            }

            Map<String, Object> definition = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "definition", "path", "src/main/java/example/App.java",
                    "line", 4, "character", 32));
            assertEquals("SUCCEEDED", definition.get("status"));
            assertFalse(((List<?>) definition.get("results")).isEmpty());

            Map<String, Object> references = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "references", "path", "src/main/java/example/App.java",
                    "line", 4, "character", 32));
            assertEquals("SUCCEEDED", references.get("status"));
            assertFalse(((List<?>) references.get("results")).isEmpty());

            Map<String, Object> hover = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "hover", "path", "src/main/java/example/App.java",
                    "line", 4, "character", 32));
            assertEquals("SUCCEEDED", hover.get("status"));

            Map<String, Object> diagnostics = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "diagnostics", "path", "src/main/java/example/App.java",
                    "line", 1, "character", 1));
            assertEquals("SUCCEEDED", diagnostics.get("status"));

            String workspaceId = "rw-lsp-refresh";
            server.call("code_enter_worktree", Map.of(
                    "repositoryId", "fixture", "baseCommit", baseCommit,
                    "workspaceId", workspaceId, "projectId", "p1", "serviceId", "svc"));
            Map<String, Object> before = server.call("code_read", Map.of(
                    "workspaceId", workspaceId, "path", "src/main/java/example/App.java"));
            server.call("code_lsp", Map.of(
                    "workspaceId", workspaceId, "action", "diagnostics",
                    "path", "src/main/java/example/App.java", "line", 1, "character", 1));
            server.call("code_apply_patch", Map.of(
                    "workspaceId", workspaceId,
                    "mode", "edit",
                    "path", "src/main/java/example/App.java",
                    "expectedSha256", before.get("sha256"),
                    "oldString", "return greeter.message();",
                    "newString", "return missingSymbol;"));
            Map<String, Object> brokenDiagnostics = server.call("code_lsp", Map.of(
                    "workspaceId", workspaceId, "action", "diagnostics",
                    "path", "src/main/java/example/App.java", "line", 1, "character", 1));
            assertEquals("SUCCEEDED", brokenDiagnostics.get("status"));
            assertFalse(((List<?>) brokenDiagnostics.get("results")).isEmpty(),
                    "diagnostics must refresh after a worktree edit");
            Map<String, Object> cleanup = server.call("code_cleanup", Map.of("workspaceId", workspaceId));
            assertEquals("CLEANED", cleanup.get("status"));
            assertEquals(true, cleanup.get("lspStopped"));
        }
    }

    @Test
    void realManagedPyrightSupportsReadOnlySemanticsAndRefreshesDiagnosticsAfterRepairEdit() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("CODE_WORKSPACE_REAL_MANAGED_PYRIGHT")),
                "real Pyright acceptance requires CODE_WORKSPACE_REAL_MANAGED_PYRIGHT=true");

        Path repository = createRepository();
        Path stateRoot = temporaryDirectory.resolve("pyright-state");
        String baseCommit = git(repository, "rev-parse", "HEAD").trim();
        try (McpProcess server = startManagedPyright(repository, stateRoot)) {
            Map<String, Object> info = server.call("code_info", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit));
            assertEquals(true, ((Map<?, ?>) info.get("capabilities")).get("lsp"));

            Map<String, Object> symbols = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "language", "python", "action", "symbols", "query", "Greeter"));
            assertEquals("SUCCEEDED", symbols.get("status"), symbols + "; serverStderr=" + server.stderr());

            Map<String, Object> definition = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "definition", "path", "python_example/app.py",
                    "line", 4, "character", 17));
            assertEquals("SUCCEEDED", definition.get("status"), definition + "; serverStderr=" + server.stderr());
            assertFalse(((List<?>) definition.get("results")).isEmpty());
            assertEquals("python", definition.get("language"));
            assertEquals("pyright", definition.get("provider"));

            Map<String, Object> references = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "references", "path", "python_example/app.py",
                    "line", 4, "character", 17));
            assertEquals("SUCCEEDED", references.get("status"));
            assertFalse(((List<?>) references.get("results")).isEmpty());

            Map<String, Object> hover = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "hover", "path", "python_example/app.py",
                    "line", 4, "character", 17));
            assertEquals("SUCCEEDED", hover.get("status"));

            String workspaceId = "rw-pyright-refresh";
            server.call("code_enter_worktree", Map.of(
                    "repositoryId", "fixture", "baseCommit", baseCommit,
                    "workspaceId", workspaceId, "projectId", "p1", "serviceId", "py"));
            Map<String, Object> before = server.call("code_read", Map.of(
                    "workspaceId", workspaceId, "path", "python_example/app.py"));
            server.call("code_lsp", Map.of(
                    "workspaceId", workspaceId, "action", "diagnostics",
                    "path", "python_example/app.py", "line", 1, "character", 1));
            server.call("code_apply_patch", Map.of(
                    "workspaceId", workspaceId,
                    "mode", "edit",
                    "path", "python_example/app.py",
                    "expectedSha256", before.get("sha256"),
                    "oldString", "return greeter.message()",
                    "newString", "return missing_symbol"));
            Map<String, Object> brokenDiagnostics = server.call("code_lsp", Map.of(
                    "workspaceId", workspaceId, "action", "diagnostics",
                    "path", "python_example/app.py", "line", 1, "character", 1));
            assertEquals("SUCCEEDED", brokenDiagnostics.get("status"));
            assertFalse(((List<?>) brokenDiagnostics.get("results")).isEmpty(),
                    "Pyright diagnostics must refresh after a worktree edit");

            Map<String, Object> afterProvision = server.call("code_info", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit));
            Map<?, ?> lsp = (Map<?, ?>) afterProvision.get("lsp");
            Map<?, ?> languages = (Map<?, ?>) lsp.get("languages");
            Map<?, ?> python = (Map<?, ?>) languages.get("python");
            assertEquals("MANAGED", python.get("mode"));
            assertEquals("1.1.410", python.get("version"));
            assertEquals(true, python.get("provisioned"));
            assertTrue(Files.isDirectory(stateRoot.resolve("runtimes/pyright/1.1.410")));

            Map<String, Object> cleanup = server.call("code_cleanup", Map.of("workspaceId", workspaceId));
            assertEquals("CLEANED", cleanup.get("status"));
            assertEquals(true, cleanup.get("lspStopped"));
        }
    }

    @Test
    void realManagedJavaAndPythonProvidersCanCoexistInOneCodeMcp() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("CODE_WORKSPACE_REAL_MANAGED_DUAL_LSP")),
                "dual LSP acceptance requires CODE_WORKSPACE_REAL_MANAGED_DUAL_LSP=true");

        Path repository = createRepository();
        Path stateRoot = temporaryDirectory.resolve("dual-lsp-state");
        String baseCommit = git(repository, "rev-parse", "HEAD").trim();
        try (McpProcess server = startManagedDualLsp(repository, stateRoot)) {
            Map<String, Object> info = server.call("code_info", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit));
            Map<?, ?> lsp = (Map<?, ?>) info.get("lsp");
            assertEquals(List.of("java", "python"), strings(lsp.get("enabledLanguages")));

            Map<String, Object> javaDefinition = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "definition", "path", "src/main/java/example/App.java",
                    "line", 4, "character", 32));
            assertEquals("SUCCEEDED", javaDefinition.get("status"));
            assertEquals("java", javaDefinition.get("language"));

            Map<String, Object> pythonDefinition = server.call("code_lsp", Map.of(
                    "repositoryId", "fixture", "revision", baseCommit,
                    "action", "definition", "path", "python_example/app.py",
                    "line", 4, "character", 17));
            assertEquals("SUCCEEDED", pythonDefinition.get("status"));
            assertEquals("python", pythonDefinition.get("language"));

            String workspaceId = "rw-dual-lsp";
            server.call("code_enter_worktree", Map.of(
                    "repositoryId", "fixture", "baseCommit", baseCommit,
                    "workspaceId", workspaceId, "projectId", "p1", "serviceId", "dual"));
            server.call("code_lsp", Map.of(
                    "workspaceId", workspaceId, "action", "diagnostics",
                    "path", "src/main/java/example/App.java", "line", 1, "character", 1));
            server.call("code_lsp", Map.of(
                    "workspaceId", workspaceId, "action", "diagnostics",
                    "path", "python_example/app.py", "line", 1, "character", 1));
            Map<String, Object> cleanup = server.call("code_cleanup", Map.of("workspaceId", workspaceId));
            assertEquals("CLEANED", cleanup.get("status"));
            assertEquals(2, number(cleanup.get("stoppedLspSessions")));
        }
    }

    private Path createRepository() throws Exception {
        Path repository = temporaryDirectory.resolve("fixture-repo");
        Files.createDirectories(repository);
        git(repository, "init");
        git(repository, "config", "user.name", "fixture");
        git(repository, "config", "user.email", "fixture@example.invalid");
        Files.writeString(repository.resolve("README.md"), "fixture\nversion=1\n");
        Files.writeString(repository.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>fixture</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>17</maven.compiler.release>
                  </properties>
                </project>
                """);
        Path source = repository.resolve("src/main/java/example");
        Files.createDirectories(source);
        Files.writeString(source.resolve("Greeter.java"), """
                package example;
                public class Greeter { public String message() { return "hi"; } }
                """);
        Files.writeString(source.resolve("App.java"), """
                package example;
                public class App {
                  private final Greeter greeter = new Greeter();
                  public String run() { return greeter.message(); }
                }
                """);
        Files.writeString(source.resolve("HealthApp.java"), """
                package example;
                import com.sun.net.httpserver.HttpServer;
                import java.net.InetSocketAddress;
                import java.nio.charset.StandardCharsets;
                public class HealthApp {
                  public static void main(String[] args) throws Exception {
                    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])), 0);
                    server.createContext("/actuator/health", exchange -> {
                      byte[] body = "UP".getBytes(StandardCharsets.UTF_8);
                      exchange.sendResponseHeaders(200, body.length);
                      exchange.getResponseBody().write(body);
                      exchange.close();
                    });
                    server.start();
                  }
                }
                """);
        Path pythonSource = repository.resolve("python_example");
        Files.createDirectories(pythonSource);
        Files.writeString(pythonSource.resolve("__init__.py"), "");
        Files.writeString(pythonSource.resolve("greeter.py"), """
                class Greeter:
                    def message(self) -> str:
                        return "hi"
                """);
        Files.writeString(pythonSource.resolve("app.py"), """
                from python_example.greeter import Greeter

                def run() -> str:
                    greeter = Greeter()
                    return greeter.message()
                """);
        git(repository, "add", "README.md", "pom.xml",
                "src/main/java/example/App.java", "src/main/java/example/Greeter.java", "src/main/java/example/HealthApp.java",
                "python_example/__init__.py", "python_example/greeter.py", "python_example/app.py");
        git(repository, "commit", "-m", "fixture base");
        return repository;
    }

    private McpProcess start(Path repository, Path stateRoot) throws IOException {
        ProcessBuilder builder = serverProcess(repository, stateRoot);
        builder.environment().remove("CODE_WORKSPACE_JDTLS_COMMAND");
        builder.environment().remove("CODE_WORKSPACE_JDTLS_ARGS_JSON");
        builder.environment().remove("CODE_WORKSPACE_PYRIGHT_COMMAND");
        builder.environment().remove("CODE_WORKSPACE_PYRIGHT_ARGS_JSON");
        return new McpProcess(builder.start());
    }

    private McpProcess startManagedJdtls(Path repository, Path stateRoot) throws IOException {
        ProcessBuilder builder = serverProcess(repository, stateRoot);
        builder.environment().remove("CODE_WORKSPACE_JDTLS_COMMAND");
        builder.environment().remove("CODE_WORKSPACE_JDTLS_ARGS_JSON");
        builder.environment().put("CODE_WORKSPACE_LSP_LANGUAGES", "java");
        builder.environment().put("CODE_WORKSPACE_JDTLS_AUTO_INSTALL", "true");
        builder.environment().put("CODE_WORKSPACE_JDTLS_VERSION", "1.43.0");
        String javaHome = System.getenv("CODE_WORKSPACE_REAL_JDTLS_JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            builder.environment().put("CODE_WORKSPACE_JDTLS_JAVA_HOME", javaHome);
        }
        return new McpProcess(builder.start());
    }

    private McpProcess startManagedPyright(Path repository, Path stateRoot) throws IOException {
        ProcessBuilder builder = serverProcess(repository, stateRoot);
        builder.environment().remove("CODE_WORKSPACE_PYRIGHT_COMMAND");
        builder.environment().remove("CODE_WORKSPACE_PYRIGHT_ARGS_JSON");
        builder.environment().put("CODE_WORKSPACE_LSP_LANGUAGES", "python");
        builder.environment().put("CODE_WORKSPACE_PYRIGHT_AUTO_INSTALL", "true");
        builder.environment().put("CODE_WORKSPACE_PYRIGHT_VERSION", "1.1.410");
        return new McpProcess(builder.start());
    }

    private McpProcess startManagedDualLsp(Path repository, Path stateRoot) throws IOException {
        ProcessBuilder builder = serverProcess(repository, stateRoot);
        builder.environment().remove("CODE_WORKSPACE_JDTLS_COMMAND");
        builder.environment().remove("CODE_WORKSPACE_JDTLS_ARGS_JSON");
        builder.environment().remove("CODE_WORKSPACE_PYRIGHT_COMMAND");
        builder.environment().remove("CODE_WORKSPACE_PYRIGHT_ARGS_JSON");
        builder.environment().put("CODE_WORKSPACE_LSP_LANGUAGES", "java,python");
        builder.environment().put("CODE_WORKSPACE_JDTLS_AUTO_INSTALL", "true");
        builder.environment().put("CODE_WORKSPACE_JDTLS_VERSION", "1.43.0");
        builder.environment().put("CODE_WORKSPACE_PYRIGHT_AUTO_INSTALL", "true");
        builder.environment().put("CODE_WORKSPACE_PYRIGHT_VERSION", "1.1.410");
        String javaHome = System.getenv("CODE_WORKSPACE_REAL_JDTLS_JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            builder.environment().put("CODE_WORKSPACE_JDTLS_JAVA_HOME", javaHome);
        }
        return new McpProcess(builder.start());
    }

    private McpProcess startWithJdtls(
            Path repository,
            Path stateRoot,
            String command,
            String argsJson) throws IOException {
        ProcessBuilder builder = serverProcess(repository, stateRoot);
        builder.environment().put("CODE_WORKSPACE_JDTLS_COMMAND", command);
        builder.environment().put("CODE_WORKSPACE_JDTLS_ARGS_JSON", argsJson);
        String javaHome = System.getenv("CODE_WORKSPACE_REAL_JDTLS_JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            builder.environment().put("JAVA_HOME", javaHome);
            builder.environment().put("PATH", Path.of(javaHome, "bin") + ":" + builder.environment().getOrDefault("PATH", ""));
        }
        return new McpProcess(builder.start());
    }

    private ProcessBuilder serverProcess(Path repository, Path stateRoot) {
        Path script = workspaceRoot().resolve("scripts/mcp/code-workspace-mcp-server.mjs");
        ProcessBuilder builder = new ProcessBuilder("node", script.toString());
        builder.environment().put("CODE_WORKSPACE_REPOSITORY_ROOT", repository.toString());
        builder.environment().put("CODE_WORKSPACE_REPOSITORY_ID", "fixture");
        builder.environment().put("CODE_WORKSPACE_STATE_ROOT", stateRoot.toString());
        builder.environment().put("CODE_WORKSPACE_TIMEOUT_MS", "10000");
        builder.environment().put("CODE_WORKSPACE_LSP_TIMEOUT_MS", "60000");
        builder.environment().put("CODE_WORKSPACE_BACKGROUND_TTL_MS", "20000");
        builder.environment().put("CODE_WORKSPACE_LSP_LANGUAGES", "");
        builder.environment().put("CODE_WORKSPACE_JDTLS_AUTO_INSTALL", "false");
        builder.environment().put("CODE_WORKSPACE_PYRIGHT_AUTO_INSTALL", "false");
        return builder;
    }

    private void waitForProcess(McpProcess server, String workspaceId, String executionId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            Map<String, Object> status = server.call("code_bash", Map.of(
                    "workspaceId", workspaceId, "action", "status", "executionId", executionId));
            if ("RUNNING".equals(status.get("status"))) return;
            Thread.sleep(50L);
        }
        throw new AssertionError("background process did not reach RUNNING");
    }

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String git(Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", command) + "\n" + output);
        }
        return output;
    }

    private int number(Object value) {
        return ((Number) value).intValue();
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Object value) {
        return ((List<Object>) value).stream().map(String::valueOf).toList();
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
            if (response.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) response.get("error");
                throw new IllegalStateException(String.valueOf(error.get("message")) + "; serverStderr=" + stderr());
            }
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
                line = lineFuture.get(120, TimeUnit.SECONDS);
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
                // The server may already have terminated due to a fatal startup error.
            } finally {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
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
