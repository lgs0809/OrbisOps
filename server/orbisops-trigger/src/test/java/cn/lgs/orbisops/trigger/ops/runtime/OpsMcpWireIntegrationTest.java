package cn.lgs.orbisops.trigger.ops.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.env.StandardEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Python process, HTTP/stdio transports, SDK and SQLite request/receipt authority. No mocked peer. */
class OpsMcpWireIntegrationTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private Process peer;
    private String base;
    private OpsMcpClientRegistry registry;
    private OpsMcpRemoteClientAdapter clients;
    private OpsMcpRemoteInvocationAdapter calls;

    @BeforeEach void start() throws Exception {
        peer = new ProcessBuilder("python3", "-u", fixture().toString(), "--database", temp.resolve("state.sqlite").toString())
                .redirectError(temp.resolve("peer.log").toFile()).start();
        String ready = new BufferedReader(new InputStreamReader(peer.getInputStream())).readLine();
        assertNotNull(ready, "MCP fixture failed to start");
        base = "http://127.0.0.1:" + json.readTree(ready).get("port").asInt();
        registry = new OpsMcpClientRegistry();
        clients = new OpsMcpRemoteClientAdapter(registry, new OpsMcpClientFactory(
                new OpsSecretResolver(new StandardEnvironment()),
                new OpsMcpTransportSecurityPolicy(OpsMcpTransportSecuritySettings.defaults())));
        calls = new OpsMcpRemoteInvocationAdapter(clients, () -> null);
    }

    @AfterEach void stop() throws Exception {
        if (registry != null) registry.invalidateAll();
        if (peer != null) { peer.destroy(); if (!peer.waitFor(3, TimeUnit.SECONDS)) peer.destroyForcibly(); }
    }

    @Test void strictWriteProjectionReachesActualSdkAndOneDurableReceiptWithoutUndeclaredActor() throws Exception {
        var application = org.mockito.Mockito.mock(cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService.class);
        var configs = org.mockito.Mockito.mock(OpsProjectMcpRuntimeConfigService.class);
        var config = config();
        org.mockito.Mockito.when(configs.resolve("wire-project", config.getMcpId())).thenReturn(Optional.of(config));
        var exactArguments = Map.<String, Object>of("testId", "strict-write", "service", "normal", "value", 9,
                "executionKey", "owned-wire-key");
        org.mockito.Mockito.when(application.execute(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest request = invocation.getArgument(0);
            assertTrue(request.trustedLandingRuntime());
            assertEquals("wire-actor", request.actor());
            assertEquals("package-wire", request.config().changePackageId());
            assertEquals("owned-wire-key", request.config().headers().get("X-Ops-Execution-Key"));
            assertEquals(exactArguments, request.input().get("arguments"));
            String output = calls.invoke(config, "append_record", json.writeValueAsString(request.input().get("arguments")));
            assertTrue(json.readTree(output).at("/normalizedContent/committed").asBoolean());
            return new cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse(true, "ALLOWED", "APPROVED_LANDING",
                    "mcp.wire", "append_record", new cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult(
                    "wire-result", "wire-evidence", output, "a".repeat(64), false, "wire:result", "b".repeat(64), 0L), Map.of());
        });
        var provider = new cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor(
                cn.lgs.orbisops.domain.toolset.model.ToolProviderType.MCP, config.getMcpId(), "MCP", "",
                config.getMcpId(), "append_record", "{}", "{}");
        var target = new cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget("mcp.wire", "append_record", "MCP", "HIGH",
                false, false, true, true, true, provider, cn.lgs.orbisops.domain.toolset.model.ToolSemantics.targetResourceWrite(),
                cn.lgs.orbisops.domain.toolset.model.ToolSchema.inputOnly("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"testId\":{},\"service\":{},\"value\":{},\"executionKey\":{}}}"));
        var request = new cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest("wire-project", "wire-user", "wire-actor",
                "mcp.wire", "append_record", cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope.APPROVED_LANDING,
                Map.of("testId", "strict-write", "service", "normal", "value", 9), "wire-session", "wire-run",
                Map.of("idempotencyKey", "owned-wire-key", "authorityDeadline", Instant.now().plusSeconds(30)),
                Map.of("changePackageId", "package-wire", "approvedPackageHash", "c".repeat(64), "approvedPackageVersion", 1));
        new cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsMcpToolExecutionDispatchHandler(
                application, new cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper(), configs).dispatch(target, request);
        assertEquals(1, count("tools/call", "strict-write"));
        assertEquals(1, json.readTree(calls.invoke(config, "receipt", "{\"testId\":\"strict-receipt\",\"executionKey\":\"owned-wire-key\"}"))
                .at("/normalizedContent/count").asInt());
    }

    @Test void compatibilityTargetUsesActualSdkSchemaForOwnedKeyAndKeepsExactBusinessArguments() throws Exception {
        var application = org.mockito.Mockito.mock(cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService.class);
        var configs = org.mockito.Mockito.mock(OpsProjectMcpRuntimeConfigService.class);
        var config = config();
        org.mockito.Mockito.when(configs.resolve("wire-project", config.getMcpId())).thenReturn(Optional.of(config));
        var mapper = new cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper();
        var business = Map.<String, Object>of("testId", "compatibility-write", "service", "normal", "value", 11);
        org.mockito.Mockito.when(application.execute(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest request = invocation.getArgument(0);
            assertTrue(request.trustedLandingRuntime());
            assertEquals(business, request.input().get("arguments"));
            var owned = mapper.legacy(request.config(), "LANDING");
            owned.setVerifiedReadOnly(false);
            String output = calls.invoke(owned, "append_record", json.writeValueAsString(request.input().get("arguments")));
            return new cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse(true, "ALLOWED", "APPROVED_LANDING",
                    "mcp.wire", "append_record", new cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult(
                    "wire-result", "wire-evidence", output, "a".repeat(64), false, "wire:result", "b".repeat(64), 0L), Map.of());
        });
        var provider = new cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor(
                cn.lgs.orbisops.domain.toolset.model.ToolProviderType.MCP, config.getMcpId(), "MCP", "",
                config.getMcpId(), "append_record", "{}", "{}");
        var target = new cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget("mcp.wire", "append_record", "MCP", "HIGH",
                false, false, true, true, true, provider, cn.lgs.orbisops.domain.toolset.model.ToolSemantics.targetResourceWrite(),
                cn.lgs.orbisops.domain.toolset.model.ToolSchema.inputOnly("{}"));
        var request = new cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest("wire-project", "wire-user", "wire-actor",
                "mcp.wire", "append_record", cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope.APPROVED_LANDING,
                business, "wire-session", "wire-run", Map.of("idempotencyKey", "compatibility-owned-key", "authorityDeadline", Instant.now().plusSeconds(30)),
                Map.of("changePackageId", "package-wire", "approvedPackageHash", "c".repeat(64), "approvedPackageVersion", 1,
                        "operationId", "operation-wire"));
        new cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsMcpToolExecutionDispatchHandler(application, mapper, configs)
                .dispatch(target, request);
        assertEquals(1, count("tools/call", "compatibility-write"));
        JsonNode receipt = evidence().get("receipts").get(0);
        assertEquals("compatibility-owned-key", receipt.get("execution_key").asText());
        assertEquals("normal", receipt.get("service_key").asText());
        assertEquals(11, receipt.get("value").asInt());
        assertEquals(1, evidence().get("receipts").size());
    }

    @ParameterizedTest @CsvSource({"spoof-key", "missing-token", "prepare-stage", "missing-approval", "missing-package", "expired-deadline", "undeclared-actor"})
    void runtimeOwnedKeyNeverRepairsInvalidAuthorityOrUndeclaredArguments(String invalid) throws Exception {
        var owned = ownedLandingConfig();
        var arguments = new LinkedHashMap<String, Object>(Map.of("testId", "denied-owned", "service", "normal", "value", 11));
        switch (invalid) {
            case "spoof-key" -> arguments.put("executionKey", "forged-key");
            case "missing-token" -> owned.setLandingRuntimeToken("");
            case "prepare-stage" -> owned.setToolCallStage("PREPARE");
            case "missing-approval" -> owned.setLandingApproved(false);
            case "missing-package" -> owned.setChangePackageId("");
            case "expired-deadline" -> owned.setAuthorityDeadline(Instant.now().minusSeconds(1));
            case "undeclared-actor" -> arguments.put("actor", "wire-actor");
            default -> throw new IllegalArgumentException(invalid);
        }
        assertThrows(RuntimeException.class, () -> calls.invoke(owned, "append_record", json.writeValueAsString(arguments)));
        assertEquals(0, count("tools/call", "denied-owned"));
        assertEquals(0, evidence().get("receipts").size());
    }

    @Test void ownedReceiptReadUsesSameNativeIdentityAndOneExistingReceipt() throws Exception {
        var owned = ownedLandingConfig();
        calls.invoke(owned, "append_record", "{\"testId\":\"owned-create\",\"service\":\"normal\",\"value\":13}");
        owned.setVerifiedReadOnly(true);
        JsonNode result = json.readTree(calls.invoke(owned, "receipt", "{\"testId\":\"owned-receipt-read\"}"));
        assertEquals(13, result.at("/normalizedContent/receipt/value").asInt());
        assertEquals("native-owned-key", result.at("/normalizedContent/receipt/execution_key").asText());
        assertEquals(1, count("tools/call", "owned-create"));
        assertEquals(1, count("tools/call", "owned-receipt-read"));
        assertEquals(1, evidence().get("receipts").size());
    }

    private OpsMcpServerConfig ownedLandingConfig() {
        return config().toBuilder().toolCallStage("LANDING").landingApproved(true).verifiedReadOnly(false)
                .internalCaller(cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter.LANDING_INTERNAL_CALLER)
                .landingRuntimeToken(cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter.LANDING_RUNTIME_TOKEN)
                .changePackageId("native-package").approvedPackageHash("c".repeat(64)).approvedPackageVersion(1)
                .operationId("native-operation").authorityDeadline(Instant.now().plusSeconds(30))
                .headers(new LinkedHashMap<>(Map.of("X-Ops-Execution-Key", "native-owned-key"))).build();
    }

    @ParameterizedTest @CsvSource({"structured,probe", "multiple,probe", "text_json,probe", "text,plain_text"})
    void completeEnvelopeMustSurviveRealSdk(String mode, String tool) throws Exception {
        JsonNode output = json.readTree(call(config(), tool, mode));
        assertEquals(1, output.get("orbisopsResultVersion").asInt());
        assertFalse(output.get("isError").asBoolean());
        if (tool.equals("plain_text")) assertTrue(output.get("normalizedContent").asText().contains("第二段"));
        else assertEquals(1, output.get("normalizedContent").get("count").asInt());
        if (mode.equals("structured")) {
            assertEquals(1, output.get("structuredContent").get("count").asInt());
            assertTrue(output.get("_meta").get("fixture").asBoolean());
        }
        if (mode.equals("multiple")) assertEquals(2, output.get("content").size());
        assertEquals(1, count("tools/call", "case"));
    }

    @ParameterizedTest @CsvSource({"gzip,structured", "gzip,text_json", "deflate,structured", "deflate,text_json"})
    void frameworkDecodesHttpEncodingBeforeStrictJsonRpc(String encoding, String mode) throws Exception {
        JsonNode output = json.readTree(calls.invoke(config(), "probe", json.writeValueAsString(Map.of(
                "testId", "encoded", "mode", mode, "contentEncoding", encoding))));
        assertEquals(1, output.at("/normalizedContent/count").asInt());
        assertEquals(1, count("tools/call", "encoded"));
    }

    @ParameterizedTest @CsvSource({"gzip,malformed,PROTOCOL_ERROR", "deflate,malformed,PROTOCOL_ERROR",
            "gzip,oversize,CONTRACT_INVALID", "deflate,oversize,CONTRACT_INVALID"})
    void compressedInvalidEnvelopeRetainsContractAndOnePhysicalAttempt(String encoding, String mode,
                                                                        OpsMcpCallFailure.Kind expected) throws Exception {
        var failure = assertThrows(OpsMcpCallFailure.class, () -> calls.invoke(config(), "probe",
                json.writeValueAsString(Map.of("testId", "encoded-invalid", "mode", mode, "contentEncoding", encoding))));
        assertEquals(expected, failure.kind());
        assertEquals(1, count("tools/call", "encoded-invalid"));
    }

    @ParameterizedTest @CsvSource({
            "tool_error,TOOL_ERROR,1", "rpc_error,PROTOCOL_ERROR,1", "rpc_internal,PROTOCOL_ERROR,2",
            "empty,CONTRACT_INVALID,1", "bad_json,CONTRACT_INVALID,1", "missing_field,CONTRACT_INVALID,1",
            "oversize,CONTRACT_INVALID,1", "malformed,PROTOCOL_ERROR,1", "http401,AUTHORITY_DENIED,1", "http403,AUTHORITY_DENIED,1"})
    void errorsMustNeverBecomeSuccessfulStrings(String mode, String kind, int physical) throws Exception {
        var error = assertThrows(OpsMcpCallFailure.class, () -> call(config(), "probe", mode));
        assertEquals(kind, error.kind().name());
        assertEquals(physical, count("tools/call", "case"));
        if (mode.equals("tool_error")) assertTrue(error.rawEnvelope().contains("NO_DATA"));
    }

    @ParameterizedTest @CsvSource({"disconnect_once", "session404_once", "sse_disconnect_once", "http503_once"})
    void readReconnectMustUseOneRetryAndFreshInitialize(String mode) throws Exception {
        long start = System.nanoTime();
        JsonNode output = json.readTree(call(config(), "probe", mode));
        assertEquals(2, output.get("normalizedContent").get("count").asInt());
        assertEquals(2, count("tools/call", "case"));
        assertEquals(2, count("initialize", null));
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 500, "fixed retry backoff");
    }

    @ParameterizedTest @CsvSource({"disconnect_always", "session404_always"})
    void repeatedDisconnectSharesOneRetryBudget(String mode) throws Exception {
        assertThrows(OpsMcpCallFailure.class, () -> call(config(), "probe", mode));
        assertEquals(2, count("tools/call", "case"));
    }

    @Test void incompatibleContractDoesNotReplayOrRefreshBeforeALaterDisconnect() throws Exception {
        var failure = assertThrows(OpsMcpCallFailure.class, () -> call(config(), "probe", "contract_then_disconnect"));
        assertEquals(OpsMcpCallFailure.Kind.CONTRACT_INVALID, failure.kind());
        assertEquals(1, count("tools/call", "case"));
        assertEquals(1, count("initialize", null));
    }

    @Test void dispatchedWriteMustNotReplayAndReceiptMustComeFromCommittedSqliteRow() throws Exception {
        var config = config(); config.setVerifiedReadOnly(false); config.setLandingApproved(true);
        var error = assertThrows(OpsMcpCallFailure.class, () -> calls.invoke(config, "append_record", json.writeValueAsString(Map.of(
                "testId", "write", "mode", "disconnect_after_commit", "executionKey", "key-1", "service", "normal", "value", 7))));
        assertTrue(error.dispatched());
        assertEquals(1, count("tools/call", "write"));
        JsonNode receipt = json.readTree(calls.invoke(config(), "receipt", "{\"testId\":\"reconcile\",\"executionKey\":\"key-1\"}"));
        assertEquals(7, receipt.at("/normalizedContent/receipt/value").asInt());
        assertEquals(1, evidence().get("receipts").size());
        assertEquals(1, count("tools/call", "write"));
    }

    @Test void processExitMustInvalidateCachedStdioHandleAndReadRetryMustRestartPeer() throws Exception {
        var config = config(); config.setTransport("stdio"); config.setCommand("python3");
        config.setArgs(List.of(fixture().toString(), "--stdio", "--database", temp.resolve("stdio.sqlite").toString()));
        JsonNode output = json.readTree(call(config, "probe", "stdio_exit_once"));
        assertEquals(2, output.at("/normalizedContent/count").asInt());
        assertEquals(1, json.readTree(calls.invoke(config, "probe", "{\"testId\":\"after-reconnect\"}")).at("/normalizedContent/count").asInt());
    }

    @Test void reusedConnectionMustHonorCurrentRunDeadlineAndNotTheFirstRun() throws Exception {
        var old = config(); old.setRunId("old"); old.setAuthorityDeadline(Instant.now().plusMillis(250));
        var oldSession = clients.open(old);
        Thread.sleep(300);
        var current = config(); current.setRunId("new"); current.setAuthorityDeadline(Instant.now().plusSeconds(30));
        assertSame(oldSession.handle(), clients.open(current).handle());
        assertEquals(1, json.readTree(call(current, "probe", "structured")).at("/normalizedContent/count").asInt());
        current.setAuthorityDeadline(Instant.now().minusMillis(1));
        assertThrows(SecurityException.class, () -> call(current, "probe", "structured"));
        assertEquals(1, count("tools/call", "case"));
    }

    @Test void schemaDriftMustBlockOldReviewedWriteBeforeDispatch() throws Exception {
        var config = config(); config.setVerifiedReadOnly(false); config.setLandingApproved(true);
        config.setVerifiedToolSchema(clients.inspectDefinition(config, "probe"));
        http.send(HttpRequest.newBuilder(URI.create(base + "/control"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"schemaRevision\":2}")).build(), HttpResponse.BodyHandlers.discarding());
        var error = assertThrows(SecurityException.class, () -> call(config, "probe", "structured"));
        assertTrue(error.getMessage().contains("CONTRACT_CHANGED"));
        assertEquals(0, count("tools/call", "case"));
    }

    @Test void unchangedReviewedContractMustPassRoundTripAndCredentialsMustNotCollide() throws Exception {
        var config = config();
        config.setVerifiedToolSchema(clients.inspectDefinition(config, "probe"));
        assertEquals(1, json.readTree(call(config, "probe", "structured")).at("/normalizedContent/count").asInt());
        var factory = new OpsMcpClientFactory(new OpsSecretResolver(new StandardEnvironment()), new OpsMcpTransportSecurityPolicy());
        config.setHeaders(Map.of("Authorization", "Bearer first-secret"));
        String first = factory.cacheKey(config);
        config.setHeaders(Map.of("Authorization", "Bearer different-secret"));
        assertNotEquals(first, factory.cacheKey(config));
        assertFalse(factory.cacheKey(config).contains("secret"));
        first = factory.cacheKey(config);
        config.setProjectId("other-project");
        assertNotEquals(first, factory.cacheKey(config));
    }

    @Test void invalidInputMustNotReachPeerOrOpenDependencyCircuit() throws Exception {
        for (int index = 0; index < 4; index++) {
            var error = assertThrows(OpsMcpCallFailure.class, () -> calls.invoke(config(), "probe", "{\"value\":1}"));
            assertEquals(OpsMcpCallFailure.Kind.CONTRACT_INVALID, error.kind());
            assertFalse(error.dispatched());
        }
        assertEquals(0, count("tools/call", null));
        call(config(), "probe", "structured");
        assertEquals(1, count("tools/call", "case"));
    }

    @Test void taskDeadlineMustCapCompleteResponseWaitAndPreventRetry() throws Exception {
        var config = config(); clients.open(config);
        config.setAuthorityDeadline(Instant.now().plusMillis(700));
        long start = System.nanoTime();
        assertThrows(RuntimeException.class, () -> calls.invoke(config, "probe", "{\"testId\":\"case\",\"mode\":\"delay\",\"delayMs\":3000}"));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed >= 600 && elapsed < 2000, "deadline elapsed=" + elapsed);
        assertEquals(1, count("tools/call", "case"));
    }

    @Test void readWaitIsFifteenSecondsPerPhysicalRequestNotPerLogicalCall() throws Exception {
        var config = config(); clients.open(config);
        config.setAuthorityDeadline(Instant.now().plusSeconds(40));
        long start = System.nanoTime();
        assertThrows(OpsMcpCallFailure.class, () -> calls.invoke(config, "probe", "{\"testId\":\"case\",\"mode\":\"delay\",\"delayMs\":16000}"));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed >= 30000 && elapsed < 36000, "two 15s waits plus 500ms, elapsed=" + elapsed);
        assertEquals(2, count("tools/call", "case"));
    }

    @Test void approvedWriteCanWaitBeyondReadLimitAndHasSixtySecondReceiptBudget() throws Exception {
        var config = config(); config.setLandingApproved(true); config.setVerifiedReadOnly(false);
        try (var scope = new OpsMcpRequestScope(config)) { assertEquals(60, scope.remaining(true).toSeconds()); }
        long start = System.nanoTime();
        JsonNode output = json.readTree(calls.invoke(config, "append_record",
                "{\"testId\":\"case\",\"mode\":\"delay\",\"delayMs\":15100,\"executionKey\":\"slow-write\"}"));
        assertEquals(1, output.at("/normalizedContent/count").asInt());
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 15000);
        assertEquals(1, count("tools/call", "case"));
        assertEquals(1, evidence().get("receipts").size());
    }

    @Test void businessErrorsMustNotConsumeDependencyCircuitBudget() throws Exception {
        for (int i = 0; i < 4; i++) assertThrows(OpsMcpCallFailure.class, () -> call(config(), "probe", "tool_error"));
        assertEquals(5, json.readTree(call(config(), "probe", "structured")).at("/normalizedContent/count").asInt());
        assertEquals(5, count("tools/call", "case"));
    }

    @Test void lateOldHandleInvalidationMustLeaveReplacementUsable() throws Exception {
        var config = config(); var old = clients.open(config).handle();
        assertTrue(clients.invalidate(old));
        var replacement = clients.open(config).handle();
        assertNotSame(old, replacement);
        assertFalse(clients.invalidate(old));
        assertThrows(OpsMcpCallFailure.class, old::assertValid);
        assertSame(replacement, clients.open(config).handle());
        call(config, "probe", "structured");
    }

    @Test void concurrentUsersMustShareReadyInitializationAndKeepEveryCall() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int index = i;
                results.add(pool.submit(() -> calls.invoke(config(), "probe", "{\"testId\":\"parallel-" + index + "\"}")));
            }
            for (var result : results) assertEquals(1, json.readTree(result.get(10, TimeUnit.SECONDS)).at("/normalizedContent/count").asInt());
            assertEquals(1, count("initialize", null));
            assertEquals(8, count("tools/call", null));
        } finally { pool.shutdownNow(); }
    }

    @ParameterizedTest @CsvSource({"true", "false"})
    void queuedCallerMustHonorItsDeadlineWithoutInvalidatingAnotherUsersCall(boolean cachedCallback) throws Exception {
        var waitingConfig = config();
        var session = clients.open(waitingConfig);
        var callback = calls.serialized(session, clients.find(session, "probe"));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var active = pool.submit(() -> calls.invoke(config(), "probe",
                    "{\"testId\":\"active\",\"mode\":\"delay\",\"delayMs\":2500}"));
            long readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (count("tools/call", "active") == 0 && System.nanoTime() < readyDeadline) Thread.sleep(10);
            assertEquals(1, count("tools/call", "active"));
            waitingConfig.setAuthorityDeadline(Instant.now().plusMillis(500));
            long start = System.nanoTime();
            assertThrows(SecurityException.class, () -> {
                if (cachedCallback) callback.call("{\"testId\":\"expired\"}");
                else calls.invoke(waitingConfig, "probe", "{\"testId\":\"expired\"}");
            });
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(elapsed >= 400 && elapsed < 1500, "lock wait elapsed=" + elapsed);
            assertFalse(active.isDone(), "queued caller must return before the unrelated active request");
            assertEquals(0, count("tools/call", "expired"));
            assertEquals(1, json.readTree(active.get(5, TimeUnit.SECONDS)).at("/normalizedContent/count").asInt());
            assertSame(session.handle(), clients.open(config()).handle());
            assertEquals(1, count("initialize", null));
        } finally { pool.shutdownNow(); }
    }

    @Test void retryMustConsumeTheSameGraphBudgetBeforeAnyPhysicalRedispatch() throws Exception {
        var used = new java.util.concurrent.atomic.AtomicInteger();
        withBudget(dispatch -> used.updateAndGet(value -> {
            if (value >= 2) throw new SecurityException("WORKFLOW_REAL_TOOL_CALL_BUDGET_EXHAUSTED:2");
            return value + 1;
        }));
        assertEquals(2, json.readTree(call(config(), "probe", "disconnect_once")).at("/normalizedContent/count").asInt());
        var denied = assertThrows(OpsMcpCallFailure.class, () -> call(config(), "probe", "structured"));
        assertEquals(OpsMcpCallFailure.Kind.AUTHORITY_DENIED, denied.kind());
        assertFalse(denied.dispatched());
        assertEquals(2, used.get());
        assertEquals(2, count("tools/call", "case"));
    }

    @Test void invalidInputAndFailedBudgetStoreMustNeverDispatchOrTripRemoteCircuit() throws Exception {
        var budgetAvailable = new java.util.concurrent.atomic.AtomicBoolean(false);
        var used = new java.util.concurrent.atomic.AtomicInteger();
        withBudget(dispatch -> {
            if (!budgetAvailable.get()) throw new IllegalStateException("synthetic durable budget store outage");
            return used.incrementAndGet();
        });
        assertThrows(OpsMcpCallFailure.class, () -> calls.invoke(config(), "probe", "{}"));
        for (int index = 0; index < 4; index++) {
            var denied = assertThrows(OpsMcpCallFailure.class, () -> call(config(), "probe", "structured"));
            assertFalse(denied.dispatched());
            assertEquals(OpsMcpCallFailure.Kind.AUTHORITY_DENIED, denied.kind());
        }
        assertEquals(0, used.get());
        assertEquals(0, count("tools/call", "case"));
        budgetAvailable.set(true);
        call(config(), "probe", "structured");
        assertEquals(1, used.get());
        assertEquals(1, count("initialize", null));
        assertEquals(1, count("tools/call", "case"));
    }

    @Test void stalledBudgetStoreMustRespectTaskDeadlineAndNeverDispatchLate() throws Exception {
        var finished = new CountDownLatch(1);
        withBudget(dispatch -> {
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1200);
            while (System.nanoTime() < end) {
                try { Thread.sleep(20); } catch (InterruptedException ignored) { }
            }
            finished.countDown();
            return 1;
        });
        var config = config(); clients.open(config);
        config.setAuthorityDeadline(Instant.now().plusMillis(500));
        long start = System.nanoTime();
        assertThrows(RuntimeException.class, () -> call(config, "probe", "structured"));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(0, count("tools/call", "case"));
    }

    @Test void durableCatalogBoundaryDoesNotRefetchOnLoadOrReconnectAndRejectsChangedDefinition() throws Exception {
        var store=new CatalogFixtureStore();
        var factory=new OpsMcpClientFactory(new OpsSecretResolver(new StandardEnvironment()),
            new OpsMcpTransportSecurityPolicy(OpsMcpTransportSecuritySettings.defaults()));
        var catalogs=new OpsMcpRemoteCatalog(new cn.lgs.orbisops.application.mcp.McpToolCatalogService(store,java.time.Clock.systemUTC()),store,factory);
        clients=new OpsMcpRemoteClientAdapter(registry,factory,catalogs);
        clients.open(config());assertEquals(1,count("tools/list",null));
        clients.open(config());assertEquals(1,count("tools/list",null));
        registry.invalidateAll();
        var reconnected=clients.open(config());assertEquals(2,count("initialize",null));assertEquals(1,count("tools/list",null));
        var loaded=clients.find(reconnected,"probe");
        http.send(HttpRequest.newBuilder(URI.create(base+"/control")).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"schemaRevision\":2}")).build(),HttpResponse.BodyHandlers.discarding());
        clients.open(config());assertEquals(1,count("tools/list",null));
        clients.refreshCatalog(config());assertEquals(2,count("tools/list",null));assertEquals(2,store.snapshot.generation());
        assertThrows(SecurityException.class,()->loaded.call("{\"testId\":\"no-stale-dispatch\"}"));
        assertEquals(0,count("tools/call",null));
        assertTrue(clients.inspectDefinition(config(),"probe").get("outputSchema").toString().contains("revision"));
        assertEquals(3,count("tools/list",null));
        clients.refreshCatalog(config());assertEquals(4,count("tools/list",null));assertEquals(2,store.snapshot.generation());
    }

    @Test void explicitDiscoveryFetchesAddedToolsAndNewSchemaWithoutChangingBusinessDispatch() throws Exception {
        var store = new CatalogFixtureStore();
        var factory = new OpsMcpClientFactory(new OpsSecretResolver(new StandardEnvironment()),
                new OpsMcpTransportSecurityPolicy(OpsMcpTransportSecuritySettings.defaults()));
        var catalogs = new OpsMcpRemoteCatalog(new cn.lgs.orbisops.application.mcp.McpToolCatalogService(
                store, java.time.Clock.systemUTC()), store, factory);
        clients = new OpsMcpRemoteClientAdapter(registry, factory, catalogs);
        var original = clients.open(config());
        assertEquals(4, original.callbacks().length);
        var priorCallback = clients.find(original, "probe");
        http.send(HttpRequest.newBuilder(URI.create(base + "/control")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"schemaRevision\":2}")).build(), HttpResponse.BodyHandlers.discarding());

        assertEquals(4, clients.open(config()).callbacks().length);
        assertEquals(1, count("tools/list", null));
        var discovered = clients.inspectDefinitions(config());
        assertEquals(5, discovered.size());
        assertTrue(discovered.stream().anyMatch(tool -> "new_catalog_read".equals(tool.get("toolName"))));
        assertTrue(discovered.stream().filter(tool -> "probe".equals(tool.get("toolName"))).findFirst()
                .orElseThrow().get("outputSchema").toString().contains("revision"));
        assertEquals(2, count("tools/list", null));
        assertEquals(2, store.snapshot.generation());
        assertThrows(SecurityException.class, () -> priorCallback.call("{\"testId\":\"prior-discovery\"}"));
        assertEquals(0, count("tools/call", null));
        clients.open(config());
        assertEquals(2, count("tools/list", null));
    }

    @Test void malformedExplicitDiscoveryRetainsThePriorCompleteGeneration() throws Exception {
        var store = new CatalogFixtureStore();
        var factory = new OpsMcpClientFactory(new OpsSecretResolver(new StandardEnvironment()),
                new OpsMcpTransportSecurityPolicy(OpsMcpTransportSecuritySettings.defaults()));
        clients = new OpsMcpRemoteClientAdapter(registry, factory, new OpsMcpRemoteCatalog(
                new cn.lgs.orbisops.application.mcp.McpToolCatalogService(store, java.time.Clock.systemUTC()), store, factory));
        clients.open(config());
        var original = store.snapshot;
        http.send(HttpRequest.newBuilder(URI.create(base + "/control")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"schemaRevision\":3}")).build(), HttpResponse.BodyHandlers.discarding());

        assertThrows(RuntimeException.class, () -> clients.inspectDefinitions(config()));
        assertSame(original, store.snapshot);
        assertEquals(2, count("tools/list", null));
        assertEquals(0, count("tools/call", null));
        assertEquals(4, clients.open(config()).callbacks().length);
        assertEquals(2, count("tools/list", null));
    }

    /** The wire test isolates the remote protocol; durable/CAS storage is separately tested in MySQL. */
    private static final class CatalogFixtureStore implements cn.lgs.orbisops.application.mcp.McpToolCatalogStore {
        Snapshot snapshot;
        public Optional<Snapshot> find(String identity) { return Optional.ofNullable(snapshot); }
        public Snapshot publish(Scope scope,long expected,String hash,String tools,Instant at) {
            long generation=snapshot==null?1:snapshot.generation()+(hash.equals(snapshot.contentHash())?0:1);
            return snapshot=new Snapshot(scope,generation,hash,tools,at,at,"");
        }
        public void failed(Scope scope,Instant at,String error) { }
        public List<Scope> scopesAfter(String after,int limit) { return List.of(); }
    }

    private void withBudget(cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository budget) {
        registry.invalidateAll();
        clients = new OpsMcpRemoteClientAdapter(registry, new OpsMcpClientFactory(new OpsSecretResolver(new StandardEnvironment()),
                new OpsMcpTransportSecurityPolicy(OpsMcpTransportSecuritySettings.defaults()),
                new cn.lgs.orbisops.application.workflow.WorkflowToolCallBudgetApplicationService(budget)));
        calls = new OpsMcpRemoteInvocationAdapter(clients, () -> null);
    }

    private String call(OpsMcpServerConfig config, String tool, String mode) throws Exception {
        return calls.invoke(config, tool, json.writeValueAsString(Map.of("testId", "case", "mode", mode)));
    }
    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder().name("fixture").mcpId("fixture").projectId("acceptance")
                .runId("run").verifiedReadOnly(true).transport("streamable-http").url(base + "/mcp").build();
    }
    private JsonNode evidence() throws Exception {
        return json.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/evidence")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    private long count(String method, String testId) throws Exception {
        long count = 0;
        for (JsonNode row : evidence().get("requests")) {
            if (row.get("method").asText().equals(method) && (testId == null || row.get("test_id").asText().equals(testId))) count++;
        }
        return count;
    }
    private Path fixture() {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path fixture = root.resolve("scripts/fixtures/mcp-acceptance-server.py");
            if (java.nio.file.Files.exists(fixture)) return fixture;
        }
        throw new IllegalStateException("MCP_ACCEPTANCE_FIXTURE_NOT_FOUND");
    }
}
