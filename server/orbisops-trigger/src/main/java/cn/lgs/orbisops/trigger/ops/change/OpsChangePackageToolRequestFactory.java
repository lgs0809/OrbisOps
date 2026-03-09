package cn.lgs.orbisops.trigger.ops.change;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the legacy-compatible PREPARE request from tool protocol input and collected evidence. */
final class OpsChangePackageToolRequestFactory {

    Map<String, Object> create(Context context,
                               OpsChangePackageToolInput input,
                               List<OpsChangePackageRunEvidenceCollector.Evidence> evidence) {
        return create(context, input, evidence, input == null ? List.of() : input.getActions());
    }

    Map<String, Object> create(Context context,
                               OpsChangePackageToolInput input,
                               List<OpsChangePackageRunEvidenceCollector.Evidence> evidence,
                               List<Map<String, Object>> governedActions) {
        if (context == null) throw new IllegalArgumentException("CHANGE_PACKAGE_TOOL_CONTEXT_REQUIRED");
        if (input == null) throw new IllegalArgumentException("ChangePackage 请求不能为空");
        List<OpsChangePackageRunEvidenceCollector.Evidence> safeEvidence =
                evidence == null ? List.of() : List.copyOf(evidence);
        if (safeEvidence.isEmpty()) {
            throw new IllegalArgumentException(
                    "当前 Run 尚无成功的 MCP、RAG、工具或权威数据源证据，不能生成 ChangePackage");
        }
        List<Map<String, Object>> actions = list(governedActions);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", context.projectId());
        request.put("runId", context.runId());
        request.put("contextBundleId", required(
                context.contextBundleId(),
                "PrepareChangePackage 缺少后端 Runtime Context Bundle ID"));
        request.put("contextBundleHash", required(
                context.contextBundleHash(),
                "PrepareChangePackage 缺少后端 Runtime Context Bundle Hash"));
        request.put("incidentId", input.getIncidentId());
        request.put("objective", text(input.getTitle()));
        request.put("question", text(input.getSummary()));
        request.put("summary", text(input.getSummary()));
        request.put("diagnosis", text(input.getDiagnosis()));
        // The preparation agent/graph is frozen by the current Work Session and
        // resolved server-side. It is intentionally not model-controlled.
        request.put("packageType", input.getPackageType());
        request.put("riskLevel", input.getRiskLevel());
        request.put("evidence", safeEvidence.stream()
                .map(OpsChangePackageRunEvidenceCollector.Evidence::toMap)
                .toList());
        request.put("mcpSteps", actions);
        request.put("candidatePlans", list(input.getCandidatePlans()));
        request.put("preflightResult", mapOrDefault(
                input.getPreflightResult(), Map.of("status", "PARTIAL")));
        request.put("dryRunResult", mapOrDefault(
                input.getDryRunResult(), Map.of("status", "NOT_SUPPORTED", "executed", false)));
        request.put("approvalBoundary", mapOrDefault(input.getApprovalBoundary(), Map.of()));
        request.put("preferredPlan", mapOrDefault(
                input.getPreferredPlan(), Map.of("steps", actions)));
        request.put("adjustmentPolicy", mapOrDefault(input.getAdjustmentPolicy(), Map.of()));
        return request;
    }

    private List<Map<String, Object>> list(List<Map<String, Object>> source) {
        return source == null
                ? List.of()
                : Collections.unmodifiableList(new java.util.ArrayList<>(source));
    }

    private Map<String, Object> mapOrDefault(Map<String, Object> source,
                                             Map<String, Object> fallback) {
        return source == null
                ? fallback
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private String required(String value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalStateException(message);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record Context(String projectId,
                   String runId,
                   String contextBundleId,
                   String contextBundleHash) {
        Context {
            projectId = textValue(projectId);
            runId = textValue(runId);
            contextBundleId = textValue(contextBundleId);
            contextBundleHash = textValue(contextBundleHash);
        }

        private static String textValue(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
