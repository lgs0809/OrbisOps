package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsToolExecutionMapper {

    public ToolExecutionRequest request(Map<String, Object> request, String actor, boolean trustedLandingRuntime) {
        Map<String, Object> safe = objectMap(request);
        String projectId = text(safe.get("projectId"));
        String userId = firstText(safe.get("userId"), actor);
        String toolsetId = required(safe.get("toolsetId"), "OpsToolExecutionService 必须提供 toolsetId");
        String toolName = required(safe.get("toolName"), "OpsToolExecutionService 必须提供 toolName");
        ToolExecutionScope scope = trustedLandingRuntime
                ? ToolExecutionScope.APPROVED_LANDING
                : ToolExecutionScope.from(safe.get("executionScope"));
        Map<String, Object> arguments = objectMap(
                safe.containsKey("arguments") ? safe.get("arguments") : safe.get("input"));
        return new ToolExecutionRequest(
                projectId,
                userId,
                text(actor),
                toolsetId,
                toolName,
                scope,
                arguments,
                text(safe.get("sessionId")),
                text(safe.get("runId")),
                safe,
                landingContext(safe, trustedLandingRuntime));
    }

    public Map<String, Object> view(ToolExecutionResponse response) {
        Map<String, Object> data = new LinkedHashMap<>(response.payload());
        data.put("resultId", response.recorded().resultId());
        data.put("preview", response.recorded().preview());
        data.put("outputHash", response.recorded().outputHash());
        data.put("truncated", response.recorded().truncated());
        data.put("fullOutputRef", response.recorded().fullOutputRef());
        data.put("inputHash", response.recorded().inputHash());
        data.put("durationMs", response.recorded().durationMs());
        data.put("evidenceId", response.recorded().evidenceId());
        data.put("toolsetId", response.target().toolsetId());
        data.put("toolName", response.target().toolName());
        data.put("allowed", response.allowed());
        data.put("decision", response.decision());
        data.put("executionScope", response.scope().name());
        return data;
    }

    private Map<String, Object> landingContext(Map<String, Object> request, boolean trustedLandingRuntime) {
        Map<String, Object> context = objectMap(request.get("metadata"));
        if (!trustedLandingRuntime) {
            context.remove("internalCaller");
            context.remove("landingRuntimeToken");
        }
        for (String key : new String[]{
                "landingApproved", "changePackageId", "approvedPackageHash",
                "approvedPackageVersion", "operationId", "packageId", "packageVersion", "packageHash"}) {
            if (request.containsKey(key)) context.put(key, request.get(key));
        }
        if (request.containsKey("packageId")) context.putIfAbsent("changePackageId", request.get("packageId"));
        if (request.containsKey("packageVersion")) context.putIfAbsent("approvedPackageVersion", request.get("packageVersion"));
        if (request.containsKey("packageHash")) context.putIfAbsent("approvedPackageHash", request.get("packageHash"));
        if (trustedLandingRuntime) {
            context.put("landingApproved", true);
            context.put("internalCaller", OpsToolsetRouter.LANDING_INTERNAL_CALLER);
            context.put("landingRuntimeToken", OpsToolsetRouter.LANDING_RUNTIME_TOKEN);
        }
        return context;
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private String required(Object value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String firstText(Object first, Object second) {
        String value = text(first);
        return value.isBlank() ? text(second) : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
