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
        if (!text(input.getServiceId()).isBlank()) request.put("serviceId", text(input.getServiceId()));
        if (input.getVerificationCriteria() != null && !input.getVerificationCriteria().isEmpty()) {
            request.put("verificationCriteria", verificationCriteria(input, actions));
        }
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

    private List<Map<String, Object>> verificationCriteria(OpsChangePackageToolInput input, List<Map<String, Object>> actions) {
        var criteria = list(input.getVerificationCriteria());
        if (criteria.size() > 16) throw new IllegalArgumentException("VERIFICATION_CRITERIA_LIMIT_EXCEEDED");
        var observable = criteria.stream().filter(item -> item != null &&
                "OBSERVABILITY_SLO_V1".equals(item.get("kind"))).toList();
        if (observable.size() > 1) throw new IllegalArgumentException("OBSERVABILITY_CRITERIA_MUST_BE_UNIQUE");
        for (var item : observable) {
            new cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowChangeVerificationPolicy().validateCriteria(item);
            if (!text(input.getServiceId()).equals(item.get("serviceId")) || !item.containsKey("minQps") || !item.containsKey("maxQps")) {
                throw new IllegalArgumentException("VERIFICATION_SERVICE_AND_LOAD_RANGE_REQUIRED");
            }
            var writes = actions.stream().filter(action ->
                    new cn.lgs.orbisops.domain.changepackage.service.ChangePackageLandingOperationSafetyPolicy().targetWrite(action)).toList();
            if (writes.isEmpty() || writes.stream().anyMatch(action ->
                    !item.get("environment").equals(action.get("targetEnvironment")) ||
                    !item.get("resourceIdentity").equals(action.get("resourceScope")))) {
                throw new IllegalArgumentException("VERIFICATION_TARGET_MUST_MATCH_GOVERNED_ACTION");
            }
        }
        return criteria.stream().map(item -> Collections.unmodifiableMap(new LinkedHashMap<>(item))).toList();
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
