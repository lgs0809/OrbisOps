package cn.lgs.orbisops.application.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainAgentCoordinatorTest {

    @Test
    void dispatchesToRegisteredHandler() {
        OpsMainAgentCoordinator coordinator = new OpsMainAgentCoordinator(List.of(handler(
                Set.of(OpsMainAgentActionType.AGENT),
                command -> OpsMainAgentOutcome.succeeded("ok", Map.of("handler", "test")))));

        OpsMainAgentOutcome outcome = coordinator.execute(command(OpsMainAgentActionType.AGENT));

        assertTrue(outcome.isSuccessful());
        assertEquals("ok", outcome.result());
        assertEquals("test", outcome.metadata().get("handler"));
    }

    @Test
    void missingHandlerFailsClosed() {
        OpsMainAgentCoordinator coordinator = new OpsMainAgentCoordinator(List.of());

        OpsMainAgentOutcome outcome = coordinator.execute(command(OpsMainAgentActionType.AGENT));

        assertEquals(OpsActionStatus.BLOCKED, outcome.status());
        assertEquals("MAIN_AGENT_HANDLER_NOT_CONFIGURED", outcome.failure().reasonCode());
        assertEquals(OpsSideEffectState.NOT_STARTED, outcome.failure().sideEffectState());
    }

    @Test
    void duplicateActionHandlerIsRejectedAtStartup() {
        OpsMainAgentActionHandler first = handler(Set.of(OpsMainAgentActionType.AGENT),
                command -> OpsMainAgentOutcome.succeeded("one", Map.of()));
        OpsMainAgentActionHandler second = handler(Set.of(OpsMainAgentActionType.AGENT),
                command -> OpsMainAgentOutcome.succeeded("two", Map.of()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OpsMainAgentCoordinator(List.of(first, second)));

        assertTrue(error.getMessage().contains("MAIN_AGENT_DUPLICATE_HANDLER"));
    }

    @Test
    void authorizationFailureIsBlockedWithoutRetry() {
        OpsMainAgentCoordinator coordinator = new OpsMainAgentCoordinator(List.of(handler(
                Set.of(OpsMainAgentActionType.AGENT),
                command -> { throw new SecurityException("APPROVAL_ROLE_REQUIRED"); })));

        OpsMainAgentOutcome outcome = coordinator.execute(command(OpsMainAgentActionType.AGENT));

        assertEquals(OpsActionStatus.BLOCKED, outcome.status());
        assertEquals("APPROVAL_ROLE_REQUIRED", outcome.failure().reasonCode());
        assertFalse(outcome.failure().retryable());
        assertEquals(OpsSideEffectState.NOT_STARTED, outcome.failure().sideEffectState());
    }

    @Test
    void unexpectedFailureNeverClaimsSafeRetry() {
        OpsMainAgentCoordinator coordinator = new OpsMainAgentCoordinator(List.of(handler(
                Set.of(OpsMainAgentActionType.AGENT),
                command -> { throw new IllegalStateException("remote disconnected"); })));

        OpsMainAgentOutcome outcome = coordinator.execute(command(OpsMainAgentActionType.AGENT));

        assertEquals(OpsActionStatus.FAILED, outcome.status());
        assertFalse(outcome.failure().retryable());
        assertEquals(OpsSideEffectState.UNKNOWN, outcome.failure().sideEffectState());
        assertTrue(outcome.failure().allowedRecoveryActions().contains("RETRY_IF_IDEMPOTENT"));
    }

    @Test
    void providerQuotaFailureIsBlockedWithActionableMessage() {
        OpsMainAgentCoordinator coordinator = new OpsMainAgentCoordinator(List.of(handler(
                Set.of(OpsMainAgentActionType.AGENT),
                command -> { throw new IllegalStateException("MODEL_PROVIDER_QUOTA_EXHAUSTED"); })));

        OpsMainAgentOutcome outcome = coordinator.execute(command(OpsMainAgentActionType.AGENT));

        assertEquals(OpsActionStatus.BLOCKED, outcome.status());
        assertEquals("MODEL_PROVIDER_QUOTA_EXHAUSTED", outcome.failure().reasonCode());
        assertEquals(OpsFailureCategory.DEPENDENCY, outcome.failure().category());
        assertFalse(outcome.failure().retryable());
        assertTrue(outcome.failure().safeUserMessage().contains("额度不足"));
        assertTrue(outcome.failure().safeUserMessage().contains("没有因此绕过审批"));
        assertTrue(outcome.failure().allowedRecoveryActions().contains("REFILL_OR_SWITCH_MODEL"));
    }

    @Test
    void nestedProviderRateLimitIsRetryableWithoutClaimingSideEffectState() {
        OpsMainAgentCoordinator coordinator = new OpsMainAgentCoordinator(List.of(handler(
                Set.of(OpsMainAgentActionType.AGENT),
                command -> { throw new IllegalStateException("runtime failed",
                        new IllegalStateException("429 - rate limit exceeded")); })));

        OpsMainAgentOutcome outcome = coordinator.execute(command(OpsMainAgentActionType.AGENT));

        assertEquals(OpsActionStatus.RETRYABLE_FAILURE, outcome.status());
        assertEquals("MODEL_PROVIDER_RATE_LIMITED", outcome.failure().reasonCode());
        assertTrue(outcome.failure().retryable());
        assertEquals(OpsSideEffectState.UNKNOWN, outcome.failure().sideEffectState());
    }

    @Test
    void rawNestedUnauthorizedProviderFailureIsClassifiedAsAuthenticationFailure() {
        OpsMainAgentCoordinator coordinator = new OpsMainAgentCoordinator(List.of(handler(
                Set.of(OpsMainAgentActionType.AGENT),
                command -> { throw new IllegalStateException("react failed",
                        new IllegalStateException("401 Unauthorized from provider")); })));

        OpsMainAgentOutcome outcome = coordinator.execute(command(OpsMainAgentActionType.AGENT));

        assertEquals(OpsActionStatus.BLOCKED, outcome.status());
        assertEquals("MODEL_PROVIDER_AUTH_FAILED", outcome.failure().reasonCode());
        assertTrue(outcome.failure().safeUserMessage().contains("认证失败"));
    }

    @Test
    void explicitUnknownSideEffectIsPreserved() {
        OpsFailureDescriptor failure = new OpsFailureDescriptor(
                "EXTERNAL_RESULT_UNKNOWN", "REMOTE_DISPATCH", OpsFailureCategory.DEPENDENCY,
                "外部系统是否执行成功暂时未知，系统不会重复发送。", false,
                OpsSideEffectState.UNKNOWN, List.of(), List.of("RECONCILE"), Map.of("operationId", "op-1"));
        OpsMainAgentCoordinator coordinator = new OpsMainAgentCoordinator(List.of(handler(
                Set.of(OpsMainAgentActionType.AGENT),
                command -> { throw new OpsMainAgentActionException(OpsActionStatus.UNKNOWN_SIDE_EFFECT, failure); })));

        OpsMainAgentOutcome outcome = coordinator.execute(command(OpsMainAgentActionType.AGENT));

        assertEquals(OpsActionStatus.UNKNOWN_SIDE_EFFECT, outcome.status());
        assertEquals("EXTERNAL_RESULT_UNKNOWN", outcome.failure().reasonCode());
        assertEquals(List.of("RECONCILE"), outcome.failure().allowedRecoveryActions());
    }

    private OpsMainAgentCommand command(OpsMainAgentActionType actionType) {
        return new OpsMainAgentCommand(actionType, "run-1", "session-1", "project-1", "user-1",
                "hello", null, Map.of());
    }

    private OpsMainAgentActionHandler handler(Set<OpsMainAgentActionType> actions,
                                              HandlerBody body) {
        return new OpsMainAgentActionHandler() {
            @Override
            public Set<OpsMainAgentActionType> supportedActions() {
                return actions;
            }

            @Override
            public OpsMainAgentOutcome handle(OpsMainAgentCommand command) {
                return body.handle(command);
            }
        };
    }

    @FunctionalInterface
    private interface HandlerBody {
        OpsMainAgentOutcome handle(OpsMainAgentCommand command);
    }
}
