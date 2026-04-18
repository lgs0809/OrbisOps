package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackageLandingOperationSafetyPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Authoritative post-check verifier for reconciled production writes. */
final class OpsLandingRecoveryPostCheckVerifier {

    private final ChangePackageLandingOperationSafetyPolicy safetyPolicy =
            new ChangePackageLandingOperationSafetyPolicy();

    Map<String, Object> verify(
            Map<String, Object> operation,
            OpsLandingOperationExecutor executor,
            Map<String, Object> context) {
        if (!safetyPolicy.targetWrite(operation)) {
            return Map.of();
        }
        Map<String, Object> postCheck = objectMap(operation.get("postCheck"));
        Map<String, Object> expected = objectMap(postCheck.get("expectedValues"));
        if (expected.isEmpty()) {
            return Map.of(
                    "reasonCode", "POST_CHECK_REQUIRED",
                    "summary", "Recovered production write lacks approved post-check expectations");
        }
        Map<String, Object> state;
        try {
            state = executor.readCurrentState(operation, context);
        } catch (RuntimeException error) {
            return Map.of(
                    "reasonCode", "POST_CHECK_READ_FAILED",
                    "error", text(error.getMessage()));
        }
        String status = text(state.get("status")).toUpperCase(Locale.ROOT);
        if (!List.of("SUCCEEDED", "PASSED").contains(status)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reasonCode", textOr(
                    state.get("reasonCode"),
                    "POST_CHECK_READ_FAILED"));
            result.put("result", state);
            return result;
        }
        Map<String, Object> actual = objectMap(state.get("currentState"));
        if (actual.isEmpty()) {
            actual = state;
        }
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object actualValue = actual.get(entry.getKey());
            if (!String.valueOf(entry.getValue()).equals(String.valueOf(actualValue))) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("reasonCode", "POST_CHECK_FAILED");
                result.put("field", entry.getKey());
                result.put("expected", entry.getValue());
                result.put("actual", actualValue);
                return result;
            }
        }
        return Map.of();
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOr(Object value, String fallback) {
        String valueText = text(value);
        return valueText.isBlank() ? fallback : valueText;
    }
}
