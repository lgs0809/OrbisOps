package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.spec.McpError;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.IdentityHashMap;

final class OpsMcpFailureClassifier {
    static java.util.Optional<OpsMcpCallFailure> findTyped(Throwable error) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof OpsMcpCallFailure typed) return java.util.Optional.of(typed);
        }
        return java.util.Optional.empty();
    }

    static OpsMcpCallFailure classify(Throwable error, boolean dispatched) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof OpsMcpCallFailure typed) return typed;
            if (cause instanceof SecurityException) {
                return failure(OpsMcpCallFailure.Kind.AUTHORITY_DENIED, cause.getMessage(), dispatched, error);
            }
            if (cause instanceof McpError rpc && rpc.getJsonRpcError() != null) {
                return failure(OpsMcpCallFailure.Kind.PROTOCOL_ERROR, "JSON_RPC_" + rpc.getJsonRpcError().code(), dispatched, error);
            }
            if (cause instanceof WebClientResponseException http) {
                int status = http.getStatusCode().value();
                var kind = status == 401 || status == 403 ? OpsMcpCallFailure.Kind.AUTHORITY_DENIED
                        : status >= 500 || status == 408 || status == 429 || status == 404
                        ? OpsMcpCallFailure.Kind.TRANSPORT_ERROR : OpsMcpCallFailure.Kind.PROTOCOL_ERROR;
                return failure(kind, "HTTP_" + status, dispatched, error);
            }
            if (cause instanceof JsonProcessingException) {
                return failure(OpsMcpCallFailure.Kind.PROTOCOL_ERROR, "MALFORMED_JSON_RPC", dispatched, error);
            }
        }
        return failure(OpsMcpCallFailure.Kind.TRANSPORT_ERROR, "REMOTE_CONNECTION_FAILURE", dispatched, error);
    }

    static boolean dependencyFailure(Exception error) {
        if (error instanceof SecurityException || error instanceof IllegalArgumentException) return false;
        if (!(error instanceof OpsMcpCallFailure typed)) return true;
        return typed.kind() == OpsMcpCallFailure.Kind.TRANSPORT_ERROR
                || typed.kind() == OpsMcpCallFailure.Kind.PROTOCOL_ERROR
                && !typed.getMessage().contains("JSON_RPC_-32602") && !typed.getMessage().contains("HTTP_4");
    }

    static boolean retryable(OpsMcpCallFailure failure) {
        return switch (failure.kind()) {
            case TRANSPORT_ERROR -> !failure.getMessage().contains("SDK_REDISPATCH_FORBIDDEN");
            // A received incompatible contract is a definitive observation, not
            // a transient connection failure. Catalog updates have their own
            // scheduled lifecycle; replaying the same request cannot repair it.
            case CONTRACT_INVALID -> false;
            case PROTOCOL_ERROR -> failure.getMessage().contains("JSON_RPC_-32603")
                    || failure.getMessage().contains("JSON_RPC_-32000");
            default -> false;
        };
    }

    private static OpsMcpCallFailure failure(OpsMcpCallFailure.Kind kind, String reason, boolean sent, Throwable cause) {
        return new OpsMcpCallFailure(kind, reason, sent, cause);
    }
}
