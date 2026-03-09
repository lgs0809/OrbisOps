package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.Map;

/** Typed revision summary around the open revision field set. */
public record ChangePackageRevisionPlan(
        String changeSummary,
        Map<String, Object> changes
) {

    public ChangePackageRevisionPlan {
        changeSummary = fallback(changeSummary, "revision");
        changes = changes == null || changes.isEmpty()
                ? Map.of()
                : Map.copyOf(CanonicalJson.copyObject(changes));
    }

    public static ChangePackageRevisionPlan from(Map<String, Object> source) {
        Map<String, Object> changes = source == null || source.isEmpty()
                ? Map.of()
                : Map.copyOf(CanonicalJson.copyObject(source));
        return new ChangePackageRevisionPlan(text(changes.get("changeSummary")), changes);
    }

    private static String fallback(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
