package cn.lgs.orbisops.trigger.ops.change;

import org.springframework.beans.factory.ObjectProvider;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Immutable adapter-type directory for landing reconciliation executors. */
final class OpsLandingOperationExecutorCatalog {

    private final Map<String, OpsLandingOperationExecutor> executors;

    OpsLandingOperationExecutorCatalog(
            ObjectProvider<OpsLandingOperationExecutor> executorProvider) {
        this.executors = executorProvider == null
                ? Map.of()
                : executorProvider.orderedStream().collect(Collectors.toUnmodifiableMap(
                        executor -> text(executor.adapterType()).toUpperCase(Locale.ROOT),
                        executor -> executor,
                        (left, right) -> left));
    }

    OpsLandingOperationExecutor find(String adapterType) {
        String key = text(adapterType).toUpperCase(Locale.ROOT);
        OpsLandingOperationExecutor executor = executors.get(key);
        return executor == null && key.startsWith("LOCAL_")
                ? executors.get("LOCAL_LANDING")
                : executor;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
