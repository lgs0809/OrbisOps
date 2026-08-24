package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsRunControlContext;
import java.util.LinkedHashMap;
import java.util.Map;

/** Projects only platform-created, pre-dispatch denials onto the current immutable Run claim. */
public final class OpsPreDispatchCheckpoint {
    private OpsPreDispatchCheckpoint() { }

    public static Map<String, Object> bind(String projectId, String runId, Map<String, Object> payload) {
        var control = OpsRunControlContext.current();
        if (control == null) return payload;
        if (!control.projectId().equals(projectId) || !control.runId().equals(runId))
            throw new IllegalStateException("TOOL_CHECKPOINT_RUN_BINDING_MISMATCH");
        Object claim = control.checkpointRequest().get("metadata");
        if (!(claim instanceof Map<?, ?> metadata)) throw new IllegalStateException("TOOL_CHECKPOINT_CLAIM_REQUIRED");
        Object attempt = metadata.get(OpsWorkSessionClaimMetadata.ATTEMPT_ID);
        Object fence = metadata.get(OpsWorkSessionClaimMetadata.FENCING_TOKEN);
        if (attempt == null || String.valueOf(attempt).isBlank() || !(fence instanceof Number))
            throw new IllegalStateException("TOOL_CHECKPOINT_CLAIM_REQUIRED");
        var result = new LinkedHashMap<String, Object>(payload);
        result.put("dispatchBoundary", "PRE_DISPATCH_DENIED");
        result.put("projectId", projectId);
        result.put("runId", runId);
        result.put("attemptId", attempt);
        result.put("fencingToken", fence);
        return Map.copyOf(result);
    }
}
