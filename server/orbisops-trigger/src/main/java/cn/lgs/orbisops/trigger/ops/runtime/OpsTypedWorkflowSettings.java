package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Controlled cutover policy for the typed bound-plan and durable runtime. */
@Component
public final class OpsTypedWorkflowSettings {

    private final Mode mode;
    private final Scope scope;
    private final int maxNodeAttempts;

    @Autowired
    public OpsTypedWorkflowSettings(
            @Value("${orbisops.workflow.typed.mode:SHADOW}") String mode,
            @Value("${orbisops.workflow.typed.scope:DURABLE_ONLY}") String scope,
            @Value("${orbisops.workflow.typed.max-node-attempts:3}") int maxNodeAttempts) {
        this.mode = Mode.parse(mode);
        this.scope = Scope.parse(scope);
        this.maxNodeAttempts = Math.max(1, Math.min(maxNodeAttempts, 20));
    }

    public OpsTypedWorkflowSettings(String mode, int maxNodeAttempts) {
        this(mode, "DURABLE_ONLY", maxNodeAttempts);
    }

    public Mode mode() {
        return mode;
    }

    public int maxNodeAttempts() {
        return maxNodeAttempts;
    }

    public Scope scope() {
        return scope;
    }

    public boolean shouldActivate(OpsAgentChatRequest request) {
        if (!mode.enabled()) return false;
        if (scope == Scope.ALL_EXPLICIT) return true;
        if (request == null || request.getMetadata() == null) return false;
        Object attemptId = request.getMetadata().get(OpsWorkSessionClaimMetadata.ATTEMPT_ID);
        Object resumedAttemptId = request.getMetadata().get("resumedFromAttemptId");
        Object explicit = request.getMetadata().get("durableWorkflow");
        return hasText(attemptId)
                || hasText(resumedAttemptId)
                || Boolean.TRUE.equals(explicit)
                || "true".equalsIgnoreCase(String.valueOf(explicit));
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).trim().isBlank();
    }

    public enum Mode {
        LEGACY,
        SHADOW,
        GUARDED;

        static Mode parse(String value) {
            String normalized = value == null
                    ? ""
                    : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (normalized.isBlank()) return SHADOW;
            try {
                return Mode.valueOf(normalized);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("TYPED_WORKFLOW_MODE_INVALID:" + normalized, error);
            }
        }

        public boolean enabled() {
            return this != LEGACY;
        }

        public boolean failClosed() {
            return this == GUARDED;
        }
    }

    public enum Scope {
        DURABLE_ONLY,
        ALL_EXPLICIT;

        static Scope parse(String value) {
            String normalized = value == null
                    ? ""
                    : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (normalized.isBlank()) return DURABLE_ONLY;
            try {
                return Scope.valueOf(normalized);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("TYPED_WORKFLOW_SCOPE_INVALID:" + normalized, error);
            }
        }
    }
}
