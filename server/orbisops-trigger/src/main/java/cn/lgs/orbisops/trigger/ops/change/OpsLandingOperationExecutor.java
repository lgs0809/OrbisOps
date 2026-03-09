package cn.lgs.orbisops.trigger.ops.change;

import java.util.Map;

public interface OpsLandingOperationExecutor {

    String adapterType();

    default Map<String, Object> readCurrentState(Map<String, Object> operation, Map<String, Object> context) {
        return Map.of("status", "NOT_SUPPORTED", "reasonCode", "DRIFT_CHECK_NOT_SUPPORTED");
    }

    default Capabilities capabilities(Map<String, Object> operation) {
        return Capabilities.unsupported();
    }

    /**
     * Queries the authoritative downstream outcome for an execution key. This
     * method must never dispatch the operation again. Executors that cannot
     * provide an authoritative query keep the operation in UNKNOWN state.
     */
    default Map<String, Object> reconcile(Map<String, Object> operation,
                                          OpsLandingExecutionContext context) {
        return Map.of("status", "NOT_SUPPORTED",
                "reasonCode", "LANDING_RECONCILIATION_NOT_SUPPORTED");
    }

    record Capabilities(boolean reconciliation) {

        public static Capabilities unsupported() {
            return new Capabilities(false);
        }
    }
}
