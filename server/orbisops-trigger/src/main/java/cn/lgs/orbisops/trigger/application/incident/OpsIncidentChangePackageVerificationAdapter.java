package cn.lgs.orbisops.trigger.application.incident;

import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentVerificationPort;
import cn.lgs.orbisops.application.incident.IncidentVerificationResult;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fail-closed verifier backed by approved ChangePackage criteria and authoritative Landing facts.
 * Natural-language legacy criteria are deliberately not interpreted as machine proof.
 */
public final class OpsIncidentChangePackageVerificationAdapter implements IncidentVerificationPort {

    private static final String OPERATION_POST_CHECKS = "LANDING_OPERATION_POST_CHECKS";

    private final ChangePackageQueryService changePackages;

    public OpsIncidentChangePackageVerificationAdapter(ChangePackageQueryService changePackages) {
        if (changePackages == null) throw new IllegalArgumentException("CHANGE_PACKAGE_QUERY_SERVICE_REQUIRED");
        this.changePackages = changePackages;
    }

    @Override
    public IncidentVerificationResult verify(IncidentSnapshot incident, String packageId) {
        Map<String, Object> detail = changePackages.detail(packageId);
        if (!text(detail.get("incidentId")).equals(incident.incidentId())) {
            return insufficient("ChangePackage 与当前 Incident 不匹配，不能用于恢复验证。",
                    "VERIFICATION_PACKAGE_INCIDENT_MISMATCH", packageId);
        }
        if (!"LANDED".equals(normalize(detail.get("status")))) {
            return insufficient("ChangePackage 尚未成功落地，不能开始恢复验证。",
                    "VERIFICATION_PACKAGE_NOT_LANDED", packageId);
        }

        List<Map<String, Object>> criteria = structuredCriteria(firstExisting(
                detail,
                "verificationCriteria",
                "verificationCriteriaJson",
                "verification_criteria_json"));
        if (criteria.isEmpty()) {
            return insufficient(
                    "当前 ChangePackage 只有自然语言或空验证条件，尚不能形成机器可核验的恢复证明。",
                    "VERIFICATION_CRITERIA_NOT_EXECUTABLE",
                    packageId);
        }
        if (criteria.stream().anyMatch(item -> !OPERATION_POST_CHECKS.equals(normalize(item.get("type"))))) {
            return insufficient(
                    "当前验证条件包含平台尚未支持的可执行类型；不会把自然语言判断冒充恢复事实。",
                    "VERIFICATION_CRITERIA_UNSUPPORTED",
                    packageId);
        }

        String landingRunId = text(detail.get("landingRunId"));
        if (landingRunId.isBlank()) {
            return insufficient("ChangePackage 缺少 LandingRun 证明。",
                    "VERIFICATION_LANDING_RUN_MISSING", packageId);
        }
        List<Map<String, Object>> operations = changePackages.landingOperationRuns(packageId, 500).stream()
                .filter(item -> landingRunId.equals(text(item.get("landingRunId"))))
                .toList();
        if (operations.isEmpty()) {
            return insufficient("没有找到本次 Landing 的 operation facts。",
                    "VERIFICATION_OPERATION_FACTS_MISSING", packageId);
        }

        List<Map<String, Object>> refs = new ArrayList<>();
        for (Map<String, Object> operation : operations) {
            String operationId = text(operation.get("operationId"));
            String factStatus = normalize(operation.get("factStatus"));
            String status = normalize(operation.get("status"));
            if ("UNKNOWN".equals(factStatus)) {
                return insufficient("存在 UNKNOWN Landing fact，必须先完成 reconciliation。",
                        "VERIFICATION_LANDING_FACT_UNKNOWN", packageId);
            }
            if (List.of("FAILED", "BLOCKED", "REJECTED").contains(status)) {
                return new IncidentVerificationResult(
                        IncidentVerificationResult.Status.FAILED,
                        "生产操作存在失败事实，恢复验证未通过。",
                        Map.of(
                                "reasonCode", "VERIFICATION_OPERATION_FAILED",
                                "packageId", packageId,
                                "landingRunId", landingRunId,
                                "operationId", operationId));
            }
            if (!"COMPLETED".equals(factStatus) || !"SUCCEEDED".equals(status)) {
                return insufficient("生产操作事实尚未收敛，暂不能宣告恢复。",
                        "VERIFICATION_OPERATION_NOT_COMPLETED", packageId);
            }
            String resultId = text(operation.get("resultId"));
            String outputHash = text(operation.get("outputHash"));
            if (resultId.isBlank() || outputHash.isBlank()) {
                return insufficient("生产操作缺少 resultId/outputHash 权威结果绑定。",
                        "VERIFICATION_OPERATION_PROOF_MISSING", packageId);
            }
            Map<String, Object> postCheck = objectMap(operation.get("postCheckResult"));
            if (!postCheckPassed(postCheck)) {
                return insufficient(
                        "Landing operation 没有可核验的 post-check 通过事实；保持 VERIFYING。",
                        "VERIFICATION_POST_CHECK_PROOF_MISSING",
                        packageId);
            }
            refs.add(Map.of(
                    "operationId", operationId,
                    "resultId", resultId,
                    "outputHash", outputHash));
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("reasonCode", "VERIFICATION_PASSED");
        evidence.put("packageId", packageId);
        evidence.put("landingRunId", landingRunId);
        evidence.put("criteria", criteria);
        evidence.put("resultRefs", List.copyOf(refs));
        return new IncidentVerificationResult(
                IncidentVerificationResult.Status.PASSED,
                "已审批的机器可执行恢复验证条件全部通过，并绑定权威 Landing resultId/outputHash。",
                evidence);
    }

    private boolean postCheckPassed(Map<String, Object> postCheck) {
        if (postCheck.isEmpty()) return false;
        String status = normalize(firstExisting(postCheck, "status", "result", "outcome"));
        if (List.of("PASSED", "SUCCEEDED", "SUCCESS", "COMPLETED", "OK").contains(status)) return true;
        Object passed = firstExisting(postCheck, "passed", "success", "matched");
        return Boolean.TRUE.equals(passed) || "true".equalsIgnoreCase(text(passed));
    }

    private IncidentVerificationResult insufficient(String summary, String reasonCode, String packageId) {
        return IncidentVerificationResult.insufficient(summary, Map.of(
                "reasonCode", reasonCode,
                "packageId", packageId));
    }

    private List<Map<String, Object>> structuredCriteria(Object value) {
        Object parsed = parse(value);
        if (!(parsed instanceof Collection<?> items) || items.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) return List.of();
            result.add(stringMap(map));
        }
        return List.copyOf(result);
    }

    private Object parse(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) return value;
        String raw = text(value);
        if (raw.startsWith("[")) {
            try {
                return JSON.parseArray(raw);
            } catch (RuntimeException ignored) {
                return List.of();
            }
        }
        return value;
    }

    private Map<String, Object> objectMap(Object value) {
        Object parsed = parse(value);
        return parsed instanceof Map<?, ?> map ? stringMap(map) : Map.of();
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) result.put(String.valueOf(key), value);
        });
        return result;
    }

    private Object firstExisting(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) return source.get(key);
        }
        return null;
    }

    private String normalize(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
