package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalActorContext;

import java.util.Map;

/** ACL from authentication/request metadata to stable approval facts. */
public record ChangePackageApprovalContext(
        String actorScope,
        boolean adminConfirmation,
        String comment) {

    public ChangePackageApprovalContext {
        actorScope = text(actorScope);
        comment = text(comment);
    }

    public static ChangePackageApprovalContext from(Map<String, ?> source) {
        Map<String, ?> safe = source == null ? Map.of() : source;
        return new ChangePackageApprovalContext(
                text(first(safe.get("actorScope"), safe.get("scope"))),
                bool(first(safe.get("adminConfirmation"), safe.get("adminConfirmed"))),
                text(safe.get("comment")));
    }

    public ChangePackageApprovalActorContext actorContext() {
        return new ChangePackageApprovalActorContext(actorScope, adminConfirmation);
    }

    private static Object first(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
