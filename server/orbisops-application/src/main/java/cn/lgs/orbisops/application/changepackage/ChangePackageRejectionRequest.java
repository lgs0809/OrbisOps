package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

public record ChangePackageRejectionRequest(String reason, String comment) {

    public ChangePackageRejectionRequest {
        reason = text(reason);
        comment = text(comment);
    }

    public static ChangePackageRejectionRequest from(Map<String, ?> source) {
        Map<String, ?> safe = source == null ? Map.of() : source;
        return new ChangePackageRejectionRequest(
                text(safe.get("reason")),
                text(safe.get("comment")));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
