package cn.lgs.orbisops.trigger.ops.change;

import com.alibaba.fastjson.JSON;

import java.util.List;
import java.util.Map;

/** Encodes the Spring AI tool result and resource-catalog description payload. */
final class OpsChangePackageToolResultRenderer {

    String statusQuery(Map<String, Object> result) {
        if (result == null) throw new IllegalStateException("CHANGE_PACKAGE_STATUS_QUERY_INCOMPLETE");
        if (Boolean.FALSE.equals(result.get("allowed"))) return JSON.toJSONString(result);
        if (!(result.get("items") instanceof List<?> items)) {
            throw new IllegalStateException("CHANGE_PACKAGE_STATUS_QUERY_INCOMPLETE");
        }
        var projected = new java.util.ArrayList<Map<String, Object>>();
        for (var item : items) {
            if (!(item instanceof Map<?, ?> source)) throw new IllegalStateException("CHANGE_PACKAGE_STATUS_QUERY_INCOMPLETE");
            var row = new java.util.LinkedHashMap<String, Object>();
            // This tool answers whether a proposal exists and its actual state. Full evidence,
            // duplicate snake/camel JSON snapshots and operation bodies stay in the source store.
            // Returning them for twenty rows made one status lookup several MB of model context.
            for (String key : List.of("packageId", "projectId", "sessionId", "incidentId", "objective", "summary",
                    "status", "version", "packageHash", "approvedVersion", "approvedPackageHash", "riskLevel",
                    "validationAssessment", "reasonCode", "legacyInvalid", "invalidReason", "runtimeExecutable",
                    "landingRunId", "createBy", "approveBy", "createTime", "updateTime", "approvedAt", "productQueue")) {
                String snake = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
                if (source.containsKey(key)) row.put(key, source.get(key));
                else if (source.containsKey(snake)) row.put(key, source.get(snake));
            }
            if (row.get("packageId") == null || row.get("status") == null) {
                throw new IllegalStateException("CHANGE_PACKAGE_STATUS_QUERY_INCOMPLETE");
            }
            projected.add(row);
        }
        var response = new java.util.LinkedHashMap<>(result);
        response.put("items", projected);
        response.put("projection", "STATUS_ONLY");
        response.put("detail", "完整方案与证据仍保存在变更详情；状态查询不回传方案正文。");
        return JSON.toJSONString(response);
    }

    String success(Map<String, Object> changePackage,
                   int evidenceCount,
                   int actionCount) {
        Map<String, Object> safePackage = changePackage == null ? Map.of() : changePackage;
        if (Boolean.FALSE.equals(safePackage.get("allowed"))
                || safePackage.get("packageId") == null || String.valueOf(safePackage.get("packageId")).isBlank()
                || safePackage.get("version") == null || safePackage.get("packageHash") == null
                || safePackage.get("status") == null) {
            throw new IllegalStateException("CHANGE_PACKAGE_CREATION_NOT_CONFIRMED");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("packageId", safePackage.get("packageId"));
        result.put("status", safePackage.get("status"));
        result.put("version", safePackage.get("version"));
        result.put("packageHash", safePackage.get("packageHash"));
        result.put("reasonCode", safePackage.get("reasonCode"));
        Object evidenceValue = safePackage.getOrDefault("evidence", safePackage.get("evidenceJson"));
        Map<?, ?> evidence = Map.of();
        if (evidenceValue instanceof Map<?, ?> map) evidence = map;
        else if (evidenceValue instanceof String serialized) {
            try {
                Object decoded = JSON.parse(serialized);
                if (decoded instanceof Map<?, ?> map) evidence = map;
            } catch (RuntimeException invalid) {
                // Missing detail is not evidence of a passed validation.
            }
        }
        result.put("limitations", evidence.get("limitations"));
        result.put("notVerifiedItems", evidence.get("notVerifiedItems"));
        result.put("riskLevel", safePackage.get("riskLevel"));
        result.put("resultId", safePackage.get("resultId"));
        result.put("evidenceId", safePackage.get("evidenceId"));
        result.put("outputHash", safePackage.get("outputHash"));
        result.put("changePackageBehavior", "PROPOSE_ONLY");
        result.put("evidenceCount", evidenceCount);
        result.put("actionCount", actionCount);
        result.put("nextStep", "VALIDATION_FAILED".equals(String.valueOf(safePackage.get("status")))
                ? "候选方案已保存，但验证失败；根据 reasonCode 和 limitations 在当前准备阶段继续修正并重新生成，不得把失败记录当作完成。不能提交审批或执行生产变更。"
                : "候选方案已保存，按真实状态继续；只有 READY_FOR_REVIEW 才能提交审核，保存不等于已审批。");
        return JSON.toJSONString(result);
    }

    String resourceCatalog(List<String> executionTargetIds) {
        return JSON.toJSONString(executionTargetIds == null ? List.of() : executionTargetIds);
    }

    String policyCatalog(List<Map<String, Object>> policies) {
        return JSON.toJSONString(policies == null ? List.of() : policies);
    }
}
