package cn.lgs.orbisops.application.changepackage;

/**
 * Stable execution identity exposed to one approved Landing run.
 *
 * <p>The operation id and execution key belong to the frozen approved plan; they are
 * deliberately separate from the ephemeral LandingRun/operation-run attempt ids.  Runtime
 * adapters use this binding both to send the same idempotency key to the target and to project
 * the returned execution fact back to the correct journal row.</p>
 */
public record LandingOperationExecutionBinding(
        String operationId,
        String executionKey,
        String toolsetId,
        String toolName,
        String resourceKey) {

    public LandingOperationExecutionBinding {
        operationId = text(operationId);
        executionKey = text(executionKey);
        toolsetId = text(toolsetId);
        toolName = text(toolName);
        resourceKey = text(resourceKey);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
