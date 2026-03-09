package cn.lgs.orbisops.trigger.ops.change;

import com.alibaba.fastjson.JSON;

import java.util.List;
import java.util.Map;

/** Encodes the Spring AI tool result and resource-catalog description payload. */
final class OpsChangePackageToolResultRenderer {

    String success(Map<String, Object> changePackage,
                   int evidenceCount,
                   int actionCount) {
        Map<String, Object> safePackage = changePackage == null ? Map.of() : changePackage;
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("packageId", safePackage.get("packageId"));
        result.put("status", safePackage.get("status"));
        result.put("version", safePackage.get("version"));
        result.put("packageHash", safePackage.get("packageHash"));
        result.put("riskLevel", safePackage.get("riskLevel"));
        result.put("resultId", safePackage.get("resultId"));
        result.put("evidenceId", safePackage.get("evidenceId"));
        result.put("outputHash", safePackage.get("outputHash"));
        result.put("changePackageBehavior", "PROPOSE_ONLY");
        result.put("evidenceCount", evidenceCount);
        result.put("actionCount", actionCount);
        result.put("nextStep", "ChangePackage 草稿已创建。平台会先执行 validation 并写回可信 proof；只有 READY_FOR_REVIEW 才能提交审核。");
        return JSON.toJSONString(result);
    }

    String resourceCatalog(List<String> executionTargetIds) {
        return JSON.toJSONString(executionTargetIds == null ? List.of() : executionTargetIds);
    }

    String policyCatalog(List<Map<String, Object>> policies) {
        return JSON.toJSONString(policies == null ? List.of() : policies);
    }
}
