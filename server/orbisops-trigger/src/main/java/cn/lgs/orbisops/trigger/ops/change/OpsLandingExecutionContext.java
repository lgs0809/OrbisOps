package cn.lgs.orbisops.trigger.ops.change;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable dispatch identity handed to every approved production executor. */
public record OpsLandingExecutionContext(String executionKey,
                                         long fencingToken,
                                         String packageId,
                                         int approvedVersion,
                                         String approvedPackageHash,
                                         String operationId,
                                         String operationHash,
                                         String actor,
                                         Instant deadline,
                                         Map<String, Object> attributes) {

    public OpsLandingExecutionContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> data = new LinkedHashMap<>(attributes);
        data.put("executionKey", text(executionKey));
        data.put("fencingToken", fencingToken);
        data.put("packageId", text(packageId));
        data.put("approvedVersion", approvedVersion);
        data.put("approvedPackageHash", text(approvedPackageHash));
        data.put("operationId", text(operationId));
        data.put("operationHash", text(operationHash));
        data.put("actor", text(actor));
        data.put("deadline", deadline == null ? "" : deadline.toString());
        return Map.copyOf(data);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
