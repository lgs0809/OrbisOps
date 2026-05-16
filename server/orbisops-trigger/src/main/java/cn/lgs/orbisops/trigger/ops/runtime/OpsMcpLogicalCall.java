package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.tool.ToolCallback;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** One retry budget shared by reconnect, transient protocol faults and contract refresh. */
final class OpsMcpLogicalCall {
    private final OpsMcpRemoteClientAdapter clients;
    private final Attempt invocation;

    OpsMcpLogicalCall(OpsMcpRemoteClientAdapter clients, Attempt invocation) {
        this.clients = clients;
        this.invocation = invocation;
    }

    String invoke(OpsMcpServerConfig config, String toolName, String input,
                  OpsMcpRemoteClientAdapter.Session initial, ToolCallback initialCallback) {
        String logicalId = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= 2; attempt++) {
            OpsMcpRemoteClientAdapter.Session session = null;
            boolean opening = true;
            try (var scope = new OpsMcpRequestScope(config, logicalId, attempt)) {
                scope.remaining(false);
                session = attempt == 1 && initial != null ? initial : clients.open(config);
                ToolCallback callback = attempt == 1 && initialCallback != null ? initialCallback : clients.find(session, toolName);
                opening = false;
                return invocation.call(session, callback, input);
            } catch (OpsMcpCallFailure failure) {
                if (session != null && failure.kind() == OpsMcpCallFailure.Kind.TRANSPORT_ERROR) clients.invalidate(session.handle());
                if (attempt == 2 || !OpsMcpFailureClassifier.retryable(failure)
                        || !Boolean.TRUE.equals(config.getVerifiedReadOnly()) && !opening && failure.dispatched()) throw failure;
                backoff(config);
            } catch (SecurityException | IllegalArgumentException denied) {
                throw denied;
            } catch (RuntimeException failure) {
                if (!opening) throw failure;
                var classified = OpsMcpFailureClassifier.classify(failure, false);
                if (attempt == 2 || !OpsMcpFailureClassifier.retryable(classified)) throw classified;
                backoff(config);
            }
        }
        throw new IllegalStateException("MCP_RETRY_BUDGET_EXHAUSTED");
    }

    private void backoff(OpsMcpServerConfig config) {
        Instant deadline = config.getAuthorityDeadline();
        if (deadline != null && Duration.between(Instant.now(), deadline).toMillis() <= 500) {
            throw new SecurityException("MCP_TASK_DEADLINE_EXHAUSTED_BEFORE_RETRY");
        }
        try { Thread.sleep(500); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SecurityException("MCP_RETRY_INTERRUPTED", interrupted);
        }
    }

    @FunctionalInterface interface Attempt {
        String call(OpsMcpRemoteClientAdapter.Session session, ToolCallback callback, String input);
    }
}
