package cn.lgs.orbisops.application.toolexecution;

import java.util.function.Supplier;

/**
 * Transaction boundary for the authoritative ToolResult/Evidence/idempotency completion write.
 * Production implementations must join the same MySQL transaction used by the underlying stores.
 */
public interface ToolExecutionTransactionPort {

    <T> T required(Supplier<T> action);

    static ToolExecutionTransactionPort direct() {
        return new ToolExecutionTransactionPort() {
            @Override
            public <T> T required(Supplier<T> action) {
                if (action == null) {
                    throw new IllegalArgumentException("TOOL_EXECUTION_TRANSACTION_ACTION_REQUIRED");
                }
                return action.get();
            }
        };
    }
}
