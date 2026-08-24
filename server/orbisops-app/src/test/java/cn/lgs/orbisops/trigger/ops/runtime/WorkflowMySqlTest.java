package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.run.*;
import cn.lgs.orbisops.domain.worksession.run.adapter.repository.IWorkSessionRunRepository;
import cn.lgs.orbisops.domain.worksession.run.model.*;
import cn.lgs.orbisops.domain.worksession.run.service.WorkSessionRunPolicy;
import cn.lgs.orbisops.domain.runtime.workflow.model.*;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;
import cn.lgs.orbisops.infrastructure.adapter.repository.JdbcWorkSessionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL, real runtime/mapper/repository, synthetic run IDs; no mock persistence. */
@EnabledIfEnvironmentVariable(named = "ORBISOPS_WORKFLOW_TEST_URL", matches = ".+orbisops_workflow_regression.*")
class WorkflowMySqlTest {
    private JdbcTemplate jdbc;
    private IWorkSessionRunRepository repository;
    private String run;
    private static final String PROJECT = "ops02-synthetic-project";
    private final WorkSessionRunPolicy policy = new WorkSessionRunPolicy();

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource(System.getenv("ORBISOPS_WORKFLOW_TEST_URL"),
                System.getenv("ORBISOPS_WORKFLOW_TEST_USER"), System.getenv("ORBISOPS_WORKFLOW_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        var factory = new ProxyFactory(new JdbcWorkSessionRunRepository(jdbc));
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new MatchAlwaysTransactionAttributeSource()));
        repository = (IWorkSessionRunRepository) factory.getProxy();
        run = "ops02-synthetic-" + UUID.randomUUID();
        System.out.println("OPS-02 synthetic MySQL run=" + run);
    }

    @Test
    void persistedResponseMustPreserveRepeatedEvidenceObjectsForStandardJsonReaders() {
        var claim = claim(60);
        var contributors = List.of(Map.of("id", "verified-mcp-policy", "readOnly", true));
        var result = Map.of("count", 4, "service", "normal");
        var response = Map.of("events", List.of(Map.of("contributors", contributors),
                        Map.of("runtimeToolContributors", contributors)),
                "mcpEnvelope", Map.of("structuredContent", result, "normalizedContent", result));
        assertTrue(repository.finish(claim, WorkSessionRunStatus.SUCCEEDED, response, "", Instant.now()));
        String raw = jdbc.queryForObject("SELECT response_json FROM ai_ops_agent_run WHERE run_id=?", String.class, run);
        assertFalse(raw.contains("\"$ref\""), "Stored evidence must not depend on Fastjson reference resolution");
        var stored = cn.lgs.orbisops.domain.shared.json.CanonicalJson.parseObject(raw);
        assertEquals(response, stored);
    }

    @Test
    void cancellationWhileWaitingMustFinishWithoutAWorkerAndForbidReclaim() {
        var claim = claim(60);
        assertTrue(repository.suspendForApproval(claim, Instant.now()));
        var waiting = repository.find(run, PROJECT).orElseThrow();
        assertTrue(repository.requestCancel(run, PROJECT, waiting.stateVersion(), "owner", "cancel wait", Instant.now()));
        assertEquals(WorkSessionRunStatus.CANCELED, repository.find(run, PROJECT).orElseThrow().status());
        assertEquals("CANCELED", jdbc.queryForObject(
                "SELECT status FROM ai_ops_agent_run_attempt WHERE attempt_id=?", String.class, claim.attemptId()));
        assertThrows(RuntimeException.class, () -> claim(60));
        assertFalse(repository.finish(claim, WorkSessionRunStatus.SUCCEEDED, Map.of(), "", Instant.now()));
    }

    @Test
    void canceledExpiredClaimMustFinishCanceledAndRejectReplay() throws Exception {
        var claim = claim(1);
        var before = repository.find(run, PROJECT).orElseThrow();
        assertTrue(repository.requestCancel(run, PROJECT, before.stateVersion(), "owner", "stop", Instant.now()));
        Thread.sleep(1200);
        var candidate = repository.findExpiredLeases(200, Instant.now()).stream()
                .filter(c -> run.equals(c.runId())).findFirst().orElseThrow();
        assertTrue(candidate.cancelRequested());
        var decision = policy.recoveryDecision(candidate);
        assertEquals(WorkSessionRunStatus.CANCELED, decision.status());
        assertTrue(repository.markRecovery(candidate, decision, Instant.now()));
        assertEquals(WorkSessionRunStatus.CANCELED, repository.find(run, PROJECT).orElseThrow().status());
        assertThrows(RuntimeException.class, () -> claim(60));
        assertFalse(repository.finish(claim, WorkSessionRunStatus.SUCCEEDED, Map.of(), "", Instant.now()));
    }

    @Test
    void canceledRunWithOnlyClaimBoundPreDispatchDenialsIsSafeToFinishCanceled() throws Exception {
        var claim = claim(1);
        Map<String, Object> proof = denialProof(claim, "TOOL_BLOCKED", false);
        assertTrue(repository.appendCheckpoint(claim, checkpoint("TOOL_EXECUTION_BLOCKED", proof)) > 0);
        var candidate = cancelAndExpire();
        assertEquals(1, candidate.toolResultCount());
        assertEquals(0, candidate.unsafeIncompleteToolCount());
        var decision = policy.recoveryDecision(candidate);
        assertEquals(WorkSessionRunStatus.CANCELED, decision.status());
        assertTrue(repository.markRecovery(candidate, decision, Instant.now()));
        assertEquals(WorkSessionRunStatus.CANCELED, repository.find(run, PROJECT).orElseThrow().status());
        assertFalse(repository.finish(claim, WorkSessionRunStatus.SUCCEEDED, Map.of(), "", Instant.now()));
        assertThrows(RuntimeException.class, () -> claim(60));
    }

    @ParameterizedTest
    @ValueSource(strings = {"legacy", "foreignProject", "foreignRun", "foreignAttempt", "foreignFence",
            "missingCall", "missingBoundary", "tamperedInput", "tamperedOutput", "tamperedCheckpoint",
            "changedTool", "persistedHashMismatch", "remoteSelfReport", "mixedLegacy", "duplicateProof", "startedWrite"})
    void cancellationMustRetainReviewWithoutCompleteNativePreDispatchProof(String failure) throws Exception {
        var claim = claim(1);
        Map<String, Object> proof = denialProof(claim,
                "remoteSelfReport".equals(failure) ? "MCP" : "TOOL_BLOCKED",
                "persistedHashMismatch".equals(failure));
        switch (failure) {
            case "foreignProject" -> proof.put("projectId", "foreign-project");
            case "foreignRun" -> proof.put("runId", "foreign-run");
            case "foreignAttempt" -> proof.put("attemptId", "foreign-attempt");
            case "foreignFence" -> proof.put("fencingToken", claim.fencingToken() + 1);
            case "missingCall" -> proof.remove("toolCallId");
            case "missingBoundary" -> proof.remove("dispatchBoundary");
            case "tamperedInput" -> proof.put("inputHash", "a".repeat(64));
            case "tamperedOutput" -> proof.put("outputHash", "b".repeat(64));
            case "changedTool" -> proof.put("toolName", "different_tool");
            default -> { }
        }
        if (!"legacy".equals(failure)) {
            var checkpoint = checkpoint("TOOL_EXECUTION_BLOCKED", proof);
            if ("tamperedCheckpoint".equals(failure)) checkpoint = new WorkSessionCheckpoint(
                    checkpoint.checkpointType(), checkpoint.payload(), "c".repeat(64), checkpoint.createdAt());
            assertTrue(repository.appendCheckpoint(claim, checkpoint) > 0);
        }
        if ("mixedLegacy".equals(failure)) denialProof(claim, "TOOL_BLOCKED", false);
        if ("duplicateProof".equals(failure))
            assertTrue(repository.appendCheckpoint(claim, checkpoint("TOOL_EXECUTION_BLOCKED", proof)) > 0);
        if ("startedWrite".equals(failure))
            assertTrue(repository.appendCheckpoint(claim, checkpoint("TOOL_EXECUTION_STARTED", Map.of(
                    "toolCallId", proof.get("toolCallId"), "readOnly", false, "writesTargetResource", true))) > 0);
        var candidate = cancelAndExpire();
        assertEquals(1, candidate.unsafeIncompleteToolCount(), failure);
        assertEquals(WorkSessionRunStatus.RECOVERY_REVIEW_REQUIRED, policy.recoveryDecision(candidate).status(), failure);
    }

    private Map<String, Object> denialProof(WorkSessionRunClaim claim, String source, boolean mismatchedStoredHash) {
        String resultId = "ops02-denial-" + UUID.randomUUID();
        String input = "{}";
        String output = "{\"remoteCallExecuted\":false}";
        String inputHash = cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(input);
        String outputHash = mismatchedStoredHash ? "d".repeat(64)
                : cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(output);
        jdbc.update("""
                INSERT INTO ai_ops_tool_result
                (result_id,project_id,session_id,run_id,user_id,toolset_id,tool_name,source,status,
                 query_text,input_hash,preview_text,full_output,full_output_ref,output_hash,truncated,
                 duration_ms,max_rows,max_bytes,max_lines,max_points,max_time_range,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, resultId, PROJECT, "session-" + run, run, "owner", "mcp.fixture", "read_state",
                source, "BLOCKED", input, inputHash, output, output, "tool-result:" + resultId,
                outputHash, 0, 0, 200, 32768, 400, 1000, 60, "owner");
        Map<String, Object> blocked = new LinkedHashMap<>();
        blocked.put("toolCallId", "blocked-call-" + resultId); blocked.put("resultId", resultId);
        blocked.put("projectId", PROJECT); blocked.put("runId", run); blocked.put("attemptId", claim.attemptId());
        blocked.put("fencingToken", claim.fencingToken()); blocked.put("dispatchBoundary", "PRE_DISPATCH_DENIED");
        blocked.put("inputHash", inputHash); blocked.put("outputHash", outputHash);
        blocked.put("toolsetId", "mcp.fixture"); blocked.put("toolName", "read_state");
        return blocked;
    }

    private WorkSessionRecoveryCandidate cancelAndExpire() throws Exception {
        var before = repository.find(run, PROJECT).orElseThrow();
        assertTrue(repository.requestCancel(run, PROJECT, before.stateVersion(), "owner", "stop", Instant.now()));
        Thread.sleep(1200);
        return repository.findExpiredLeases(200, Instant.now()).stream()
                .filter(c -> run.equals(c.runId())).findFirst().orElseThrow();
    }

    @Test
    void repeatedCancelMustSettleLegacyCanceledRecoverableRun() throws Exception {
        claim(1);
        var before = repository.find(run, PROJECT).orElseThrow();
        assertTrue(repository.requestCancel(run, PROJECT, before.stateVersion(), "owner", "stop", Instant.now()));
        Thread.sleep(1200);
        var candidate = repository.findExpiredLeases(200, Instant.now()).stream()
                .filter(c -> run.equals(c.runId())).findFirst().orElseThrow();
        // Reproduce the old policy's durable outcome through the repository command, not SQL.
        assertTrue(repository.markRecovery(candidate, new WorkSessionRecoveryDecision(
                run, PROJECT, candidate.attemptId(), WorkSessionRunStatus.RECOVERABLE,
                "LEASE_EXPIRED_BEFORE_TOOL_EXECUTION"), Instant.now()));
        var legacy = repository.find(run, PROJECT).orElseThrow();
        assertTrue(repository.requestCancel(run, PROJECT, legacy.stateVersion(), "owner", "stop", Instant.now()));
        assertEquals(WorkSessionRunStatus.CANCELED, repository.find(run, PROJECT).orElseThrow().status());
        assertThrows(RuntimeException.class, () -> claim(60));
    }

    @Test
    void cancellationCommittedBeforeCompletionMustFenceSuccess() {
        var claim = claim(60);
        var before = repository.find(run, PROJECT).orElseThrow();
        assertTrue(repository.requestCancel(run, PROJECT, before.stateVersion(), "owner", "cancel test", Instant.now()));
        assertFalse(repository.finish(claim, WorkSessionRunStatus.SUCCEEDED, Map.of(), "", Instant.now()));
        assertTrue(repository.finish(claim, WorkSessionRunStatus.CANCELED, Map.of(), "cancel test", Instant.now()));
        assertEquals(WorkSessionRunStatus.CANCELED, repository.find(run, PROJECT).orElseThrow().status());
    }

    @Test
    void concurrentCancellationAndCompletionMustHaveOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 20; i++) {
                run = "ops02-race-" + UUID.randomUUID();
                var claim = claim(60);
                var before = repository.find(run, PROJECT).orElseThrow();
                var start = new CountDownLatch(1);
                Future<Boolean> canceled = pool.submit(() -> { start.await(); return repository.requestCancel(
                        claim.runId(), PROJECT, before.stateVersion(), "owner", "race", Instant.now()); });
                Future<Boolean> completed = pool.submit(() -> { start.await(); return repository.finish(
                        claim, WorkSessionRunStatus.SUCCEEDED, Map.of(), "", Instant.now()); });
                start.countDown();
                boolean cancelWon = canceled.get(10, TimeUnit.SECONDS);
                boolean completeWon = completed.get(10, TimeUnit.SECONDS);
                assertNotEquals(cancelWon, completeWon, "cancel and success must not both commit");
                if (cancelWon) assertTrue(repository.finish(claim, WorkSessionRunStatus.CANCELED, Map.of(), "race", Instant.now()));
                assertEquals(cancelWon ? WorkSessionRunStatus.CANCELED : WorkSessionRunStatus.SUCCEEDED,
                        repository.find(run, PROJECT).orElseThrow().status());
            }
        } finally { pool.shutdownNow(); }
    }

    @Test
    void expiredLeaseMustRejectLateWritesAndHeartbeatEvenBeforeRecoveryClaimsIt() throws Exception {
        var old = claim(1);
        Thread.sleep(1200);
        assertEquals(0, repository.appendCheckpoint(old, checkpoint("LATE", Map.of())));
        assertFalse(repository.heartbeat(old, Instant.now().plusSeconds(60), Instant.now()));
        assertFalse(repository.bindManifest(old, Map.of(), "changed", checkpoint("LATE_MANIFEST", Map.of()), Instant.now()));
        assertFalse(repository.suspendForApproval(old, Instant.now()));
        assertFalse(repository.finish(old, WorkSessionRunStatus.SUCCEEDED, Map.of(), "", Instant.now()));
        var candidate = repository.findExpiredLeases(200, Instant.now()).stream()
                .filter(c -> run.equals(c.runId())).findFirst().orElseThrow();
        assertTrue(repository.markRecovery(candidate, policy.recoveryDecision(candidate), Instant.now()));
        var current = claim(60);
        assertTrue(current.fencingToken() > old.fencingToken());
        assertEquals(0, repository.appendCheckpoint(old, checkpoint("OLD_EPOCH", Map.of())));
        assertTrue(repository.appendCheckpoint(current, checkpoint("NEW_EPOCH", Map.of())) > 0);
    }

    @Test
    void checkpointInsertFailureMustRollbackSequenceAndLeaveRuntimeAtPriorState() {
        var claim = claim(60);
        var request = request(claim);
        var coordinator = coordinator();
        coordinator.start(request, plan(), Map.of());
        long before = jdbc.queryForObject("SELECT next_checkpoint_seq FROM ai_ops_agent_run WHERE run_id=?", Long.class, run);
        String trigger = "ops02_fault_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        faultSql("CREATE TRIGGER " + trigger + " BEFORE INSERT ON ai_ops_agent_run_checkpoint FOR EACH ROW BEGIN "
                + "IF NEW.run_id='" + run + "' AND NEW.checkpoint_type='WORKFLOW_NODE_BEFORE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='OPS02 injected checkpoint failure'; END IF; END");
        try {
            assertThrows(RuntimeException.class, () -> coordinator.beforeNode(request, "a", 3));
            assertEquals(0, coordinator.state(request).node("a").attempt());
            assertEquals(before, jdbc.queryForObject("SELECT next_checkpoint_seq FROM ai_ops_agent_run WHERE run_id=?", Long.class, run));
            assertEquals("WORKFLOW_RUN_STARTED", repository.latestCheckpoint(run, PROJECT, "WORKFLOW_").orElseThrow().checkpointType());
        } finally { faultSql("DROP TRIGGER " + trigger); }
        assertEquals(1, coordinator.beforeNode(request, "a", 3).node("a").attempt());
    }

    @Test
    void parallelCompletionsMustPersistBothOutputsAndRecoverTheirJoin() throws Exception {
        var request = request(claim(60));
        var coordinator = coordinator();
        coordinator.start(request, plan(), Map.of());
        coordinator.beforeNode(request, "a", 3);
        coordinator.beforeNode(request, "b", 3);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return coordinator.afterNode(request, "a", "a-output", Map.of("a-result", 10)); });
            var b = pool.submit(() -> { gate.await(); return coordinator.afterNode(request, "b", "b-output", Map.of("b-result", 20)); });
            gate.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        var recovered = coordinator().recover(request, plan());
        assertEquals(10, ((Number) recovered.variables().get("a-result")).intValue());
        assertEquals(20, ((Number) recovered.variables().get("b-result")).intValue());
        assertTrue(recovered.nodeStates().values().stream().allMatch(n -> n.status() == DurableWorkflowNodeStatus.SUCCEEDED));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_agent_run_checkpoint WHERE run_id=? AND checkpoint_type='WORKFLOW_NODE_AFTER'", Integer.class, run));
    }

    @Test
    void lostWriteResponseMustRequireReviewWhileReadOnlyInterruptionIsRecoverable() throws Exception {
        for (boolean write : List.of(true, false)) {
            run = "ops02-lost-response-" + UUID.randomUUID();
            var claim = claim(1);
            repository.appendCheckpoint(claim, checkpoint("TOOL_EXECUTION_STARTED", Map.of(
                    "toolCallId", "call-1", "readOnly", !write, "writesTargetResource", write)));
            // This models transport loss after dispatch. No fabricated completion/result is inserted.
            Thread.sleep(1200);
            var candidate = repository.findExpiredLeases(200, Instant.now()).stream()
                    .filter(c -> run.equals(c.runId())).findFirst().orElseThrow();
            var decision = policy.recoveryDecision(candidate);
            assertEquals(write ? WorkSessionRunStatus.RECOVERY_REVIEW_REQUIRED : WorkSessionRunStatus.RECOVERABLE, decision.status());
            assertTrue(repository.markRecovery(candidate, decision, Instant.now()));
            if (write) assertThrows(IllegalStateException.class, () -> claim(60));
            else assertTrue(claim(60).fencingToken() > claim.fencingToken());
        }
    }

    private WorkSessionRunClaim claim(int seconds) {
        Instant now = Instant.now();
        return repository.claim(new WorkSessionRunStart(run, PROJECT, "session-" + run, "owner", "agent-ops02", 1,
                "definition-hash", "PROJECT_PRE_APPROVAL", Map.of("synthetic", true), "manifest-hash", Map.of(),
                "attempt-" + UUID.randomUUID(), "lease-" + UUID.randomUUID(), "test-worker", now, now.plusSeconds(seconds)),
                checkpoint("RUN_CLAIMED", Map.of()));
    }

    @Test
    void realHttpWriteWithLostResponseMustRemainInReconciliationAndNeverRedispatch() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ops02_http_write_target (request_key VARCHAR(100) PRIMARY KEY, applied_count INT NOT NULL)");
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory(Map.of("jdbc", jdbc));
        var ledger = new cn.lgs.orbisops.infrastructure.adapter.repository.JdbcToolExecutionIdempotencyAdapter(
                beans.getBeanProvider(JdbcTemplate.class), true);
        ledger.initialize();
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var applied = new CountDownLatch(1);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        final String key = run;
        server.createContext("/write", exchange -> {
            exchange.getRequestBody().readAllBytes();
            hits.incrementAndGet();
            jdbc.update("INSERT INTO ops02_http_write_target VALUES (?,1) ON DUPLICATE KEY UPDATE applied_count=applied_count+1", key);
            applied.countDown();
            exchange.close(); // Business write committed, connection lost before any response headers.
        });
        server.start();
        try {
            Instant now = Instant.now();
            var command = new cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort.ReserveCommand(
                    key, PROJECT, run, "http-write", 1, 1, "a".repeat(64), "b".repeat(64), true,
                    Map.of("requestKey", key, "target", "loopback-http-regression"), "owner", now, now.plusSeconds(30));
            var reservation = ledger.reserve(command);
            assertEquals(cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort.Disposition.EXECUTE, reservation.disposition());
            try (var socket = new java.net.Socket("127.0.0.1", server.getAddress().getPort())) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write("POST /write HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                assertTrue(applied.await(5, TimeUnit.SECONDS));
                assertEquals(-1, socket.getInputStream().read());
            }
            ledger.fail(new cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort.FailCommand(
                    key, "owner", reservation.fencingToken(), "TRANSPORT_RESULT_UNKNOWN", "HTTP response lost after dispatch", true, Instant.now()));
            assertEquals(cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort.Disposition.REVIEW_REQUIRED,
                    ledger.reserve(command).disposition());
            assertTrue(ledger.hasUnresolvedSideEffect(PROJECT, run));
            assertEquals(1, hits.get());
            assertEquals(1, jdbc.queryForObject("SELECT applied_count FROM ops02_http_write_target WHERE request_key=?", Integer.class, key));
            assertEquals("REVIEW_REQUIRED", jdbc.queryForObject("SELECT status FROM ai_ops_tool_execution_ledger WHERE idempotency_key=?", String.class, key));
        } finally { server.stop(0); }
    }

    private void faultSql(String sql) {
        // Bind fault injection to the same isolated schema as the tested repository.
        // Root is used only because MySQL binary logging requires elevated trigger creation.
        // The tested application repository still uses its own schema-scoped user.
        try {
            String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
            assertNotNull(database);
            assertTrue(database.matches("orbisops_workflow_regression(?:_[a-z0-9_]+)?"),
                    "Fault injection must stay in the explicitly isolated regression schema");
            var process = new ProcessBuilder("docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
                    "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" exec mysql -uroot \"$1\"", "workflow-fault", database)
                    .redirectErrorStream(true).start();
            String input = sql.startsWith("CREATE") ? "DELIMITER //\n" + sql + "//\n" : sql + ";\n";
            try (var stream = process.getOutputStream()) { stream.write(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), output);
        } catch (Exception error) { throw new IllegalStateException("fault injection failed", error); }
    }

    private WorkSessionCheckpoint checkpoint(String type, Map<String, Object> payload) {
        return policy.checkpoint(type, payload, Instant.now());
    }

    private OpsDurableWorkflowRuntimeCoordinator coordinator() {
        var service = new WorkSessionRunApplicationService(repository, new WorkSessionRunIdentityPort() {
            public String newAttemptId() { return UUID.randomUUID().toString(); }
            public String newLeaseToken() { return UUID.randomUUID().toString(); }
            public String workerId() { return "test-worker"; }
            public Instant now() { return Instant.now(); }
        }, Duration.ofSeconds(60));
        return new OpsDurableWorkflowRuntimeCoordinator(new OpsWorkSessionRunAdapter(service, new OpsWorkSessionRunMapper()));
    }

    private OpsAgentChatRequest request(WorkSessionRunClaim claim) {
        return OpsAgentChatRequest.builder().runId(run).projectId(PROJECT).sessionId("session-" + run)
                .metadata(new LinkedHashMap<>(Map.of(OpsWorkSessionClaimMetadata.ATTEMPT_ID, claim.attemptId(),
                        OpsWorkSessionClaimMetadata.LEASE_TOKEN, claim.leaseToken(),
                        OpsWorkSessionClaimMetadata.FENCING_TOKEN, claim.fencingToken(),
                        OpsWorkSessionClaimMetadata.STATE_VERSION, claim.stateVersion(),
                        OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH, claim.runManifestHash()))).build();
    }

    private BoundWorkflowExecutionPlan plan() {
        var nodes = List.of(new BoundWorkflowNode("a", "START", "START", "boundary-node", "a-config", List.of()),
                new BoundWorkflowNode("b", "END", "END", "boundary-node", "b-config", List.of()));
        var resources = List.of(new BoundWorkflowResourceSnapshot(BoundWorkflowResourceKind.MEMORY_CONTEXT,
                        "test-bundle", 0, "context-hash", "RUNTIME_CONTEXT_BUNDLE", true, true),
                new BoundWorkflowResourceSnapshot(BoundWorkflowResourceKind.RUNTIME_POLICY,
                        "test-policy", 1, "policy-hash", "RUNTIME_CONTEXT_BUNDLE", true, true));
        var stages = List.of("ACCESS_VALIDATION", "BOUND_PLAN_ASSEMBLY");
        String hash = new BoundWorkflowPlanPolicy().calculatePlanHash(1, 1, "definition-hash", "agent-ops02",
                "session-" + run, run, PROJECT, "test-bundle", "context-hash", "a", nodes, List.of(), resources, stages);
        return new BoundWorkflowExecutionPlan(1, 1, "definition-hash", "agent-ops02", "session-" + run, run, PROJECT,
                "test-bundle", "context-hash", "a", nodes, List.of(), resources, hash, Instant.now(), stages);
    }
}
