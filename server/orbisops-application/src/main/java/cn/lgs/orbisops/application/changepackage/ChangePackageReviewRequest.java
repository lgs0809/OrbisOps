package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

public record ChangePackageReviewRequest(String comment) {

    public ChangePackageReviewRequest {
        comment = text(comment);
    }

    public static ChangePackageReviewRequest from(Map<String, ?> source) {
        Map<String, ?> safe = source == null ? Map.of() : source;
        return new ChangePackageReviewRequest(text(safe.get("comment")));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
