package cn.lgs.orbisops.trigger.ops.change;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the stable audit payload for PREPARE risk escalation. */
final class OpsPreparationAuditPayloadFactory {

    AuditPayload riskEscalated(Map<String, Object> request, String systemRiskLevel) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("requestedRiskLevel", text(safe.get("riskLevel")));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("systemRiskLevel", text(systemRiskLevel));
        after.put("reason", "REQUEST_RISK_LOWER_THAN_SYSTEM_ASSESSMENT");
        return new AuditPayload(
                text(safe.get("sessionId")),
                before,
                after);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record AuditPayload(String targetId,
                        Map<String, Object> before,
                        Map<String, Object> after) {
        AuditPayload {
            targetId = targetId == null ? "" : targetId.trim();
            before = immutableCopy(before);
            after = immutableCopy(after);
        }

        private static Map<String, Object> immutableCopy(Map<String, Object> source) {
            return source == null || source.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }
}
