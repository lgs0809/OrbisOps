package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.common.McpTransportContext;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** One physical attempt. Captured by the SDK context supplier before switching reactor threads. */
final class OpsMcpRequestScope implements AutoCloseable {
    static final String KEY = "orbisops.mcp.request";
    private static final ThreadLocal<OpsMcpRequestScope> CURRENT = new ThreadLocal<>();
    private final OpsMcpRequestScope previous;
    private final OpsMcpServerConfig config;
    private final String logicalCallId;
    private final int attempt;
    private final AtomicBoolean dispatched = new AtomicBoolean();
    private final AtomicBoolean claimed = new AtomicBoolean();
    private volatile int budgetUsed;
    private volatile String requestId = "";
    private volatile Map<String, Object> argumentProjectionAudit = Map.of();

    OpsMcpRequestScope(OpsMcpServerConfig config) {
        this(config, CURRENT.get() == null ? java.util.UUID.randomUUID().toString() : CURRENT.get().logicalCallId,
                CURRENT.get() == null ? 1 : CURRENT.get().attempt);
    }

    OpsMcpRequestScope(OpsMcpServerConfig config, String logicalCallId, int attempt) {
        this.config = config;
        this.logicalCallId = logicalCallId;
        this.attempt = attempt;
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    static McpTransportContext transportContext() {
        var current = CURRENT.get();
        return current == null ? McpTransportContext.EMPTY : McpTransportContext.create(Map.of(KEY, current));
    }

    Duration remaining(boolean toolCall) {
        long seconds = toolCall && Boolean.TRUE.equals(config.getLandingApproved())
                && !Boolean.TRUE.equals(config.getVerifiedReadOnly()) ? 60L : 15L;
        Duration wait = Duration.ofSeconds(seconds);
        if (config.getAuthorityDeadline() != null) {
            Duration remaining = Duration.between(Instant.now(), config.getAuthorityDeadline());
            if (remaining.isNegative() || remaining.isZero()) {
                throw new SecurityException("MCP_TASK_DEADLINE_EXHAUSTED");
            }
            if (remaining.compareTo(wait) < 0) wait = remaining;
        }
        return wait;
    }

    void dispatch(Object id,
            java.util.function.ToIntFunction<cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository.Dispatch> budget) {
        remaining(true);
        if (!claimed.compareAndSet(false, true)) {
            throw new OpsMcpCallFailure(OpsMcpCallFailure.Kind.TRANSPORT_ERROR, "SDK_REDISPATCH_FORBIDDEN", true);
        }
        requestId = String.valueOf(id);
        try {
            budgetUsed = budget.applyAsInt(new cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository.Dispatch(
                    config.getProjectId(), config.getRunId(), text(config.getNodeId()), text(config.getMcpId()),
                    text(config.getVerifiedToolSchema() == null ? null : config.getVerifiedToolSchema().get("toolName")),
                    logicalCallId, attempt, requestId));
        } catch (RuntimeException denied) {
            throw new OpsMcpCallFailure(OpsMcpCallFailure.Kind.AUTHORITY_DENIED,
                    "WORKFLOW_TOOL_DISPATCH_DENIED:" + denied.getMessage(), false, denied);
        }
        remaining(true); // A blocked/slow budget store cannot permit an expired request to dispatch.
        dispatched.set(true);
    }

    boolean dispatched() { return dispatched.get(); }
    static boolean currentDispatched() { return CURRENT.get() != null && CURRENT.get().dispatched(); }

    static void acquire(java.util.concurrent.locks.ReentrantLock lock) {
        var scope = CURRENT.get();
        Duration wait = scope == null ? Duration.ofSeconds(15) : scope.remaining(false);
        try {
            if (!lock.tryLock(wait.toNanos(), java.util.concurrent.TimeUnit.NANOSECONDS)) {
                throw new SecurityException("MCP_CLIENT_LOCK_WAIT_EXHAUSTED");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SecurityException("MCP_CLIENT_LOCK_WAIT_INTERRUPTED", interrupted);
        }
    }

    static void assertContract(Map<String, Object> inputSchema, Map<String, Object> outputSchema) {
        var scope = CURRENT.get();
        var expected = scope == null ? null : scope.config.getVerifiedToolSchema();
        if (expected == null || !expected.containsKey("inputSchema")) return;
        Object expectedOutput = expected.get("outputSchema");
        if (!hash(inputSchema).equals(hash(expected.get("inputSchema")))
                || !hash(outputSchema).equals(hash(expectedOutput == null ? Map.of() : expectedOutput))) {
            throw new SecurityException("MCP_POLICY_STALE:REMOTE_TOOL_CONTRACT_CHANGED_REVIEW_REQUIRED");
        }
    }

    static Map<String, Object> runtimeOwnedArguments(Map<String, Object> schema, Map<String, Object> arguments) {
        var scope = CURRENT.get();
        if (scope == null) return arguments;
        scope.remaining(true);
        var projected = OpsMcpRuntimeOwnedArguments.project(scope.config, schema, arguments);
        if (!projected.equals(arguments)) {
            scope.argumentProjectionAudit = Map.of("runtimeArgumentHash", hash(projected),
                    "runtimeOwnedArgumentFields", projected.keySet().stream()
                            .filter(field -> !arguments.containsKey(field)).sorted().toList());
        }
        return projected;
    }

    private static String hash(Object value) {
        return cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(value);
    }

    static Map<String, Object> audit() {
        var scope = CURRENT.get();
        if (scope == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", scope.requestId);
        result.put("logicalCallId", scope.logicalCallId);
        result.put("physicalAttempt", scope.attempt);
        result.put("dispatched", scope.dispatched());
        result.put("readOnly", Boolean.TRUE.equals(scope.config.getVerifiedReadOnly()));
        result.putAll(scope.argumentProjectionAudit);
        if (scope.budgetUsed > 0) result.put("workflowRealToolCallsUsed", scope.budgetUsed);
        var schema = scope.config.getVerifiedToolSchema();
        if (schema != null) for (String field : java.util.List.of("riskLevel", "policyId", "schemaHash")) {
            if (schema.get(field) != null) result.put(field, schema.get(field));
        }
        if (scope.config.getAuthorityDeadline() != null) result.put("deadline", scope.config.getAuthorityDeadline().toString());
        return Map.copyOf(result);
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
