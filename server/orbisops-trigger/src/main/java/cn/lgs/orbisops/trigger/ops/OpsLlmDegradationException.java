package cn.lgs.orbisops.trigger.ops;

/**
 * Raised when an LLM-driven agent path would otherwise silently fall back to
 * deterministic rules.
 */
public class OpsLlmDegradationException extends RuntimeException {

    public OpsLlmDegradationException(String agentName, String reason) {
        super(message(agentName, reason));
    }

    public OpsLlmDegradationException(String agentName, String reason, Throwable cause) {
        super(message(agentName, reason), cause);
    }

    private static String message(String agentName, String reason) {
        return "%s LLM 降级被禁止：%s".formatted(agentName, reason);
    }

}
