package cn.lgs.orbisops.domain.changepackage.model;

/** Typed result of validating one approved Landing operation before dispatch. */
public record ChangePackageLandingOperationSafetyDecision(Kind kind,
                                                          String reasonCode,
                                                          String summary,
                                                          String replanTrigger) {

    public ChangePackageLandingOperationSafetyDecision {
        if (kind == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_OPERATION_SAFETY_KIND_REQUIRED");
        }
        reasonCode = text(reasonCode);
        summary = text(summary);
        replanTrigger = text(replanTrigger);
        if (kind == Kind.ALLOWED) {
            if (!reasonCode.isBlank() || !summary.isBlank() || !replanTrigger.isBlank()) {
                throw new IllegalArgumentException("CHANGE_PACKAGE_ALLOWED_OPERATION_SAFETY_MUST_BE_CLEAN");
            }
        } else if (reasonCode.isBlank() || summary.isBlank() || replanTrigger.isBlank()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_OPERATION_SAFETY_REJECTION_INCOMPLETE");
        }
    }

    public static ChangePackageLandingOperationSafetyDecision allowed() {
        return new ChangePackageLandingOperationSafetyDecision(Kind.ALLOWED, "", "", "");
    }

    public static ChangePackageLandingOperationSafetyDecision rejected(String reasonCode,
                                                                       String summary,
                                                                       String replanTrigger) {
        return new ChangePackageLandingOperationSafetyDecision(
                Kind.REJECTED,
                reasonCode,
                summary,
                replanTrigger);
    }

    public boolean allowedOperation() {
        return kind == Kind.ALLOWED;
    }

    public enum Kind {
        ALLOWED,
        REJECTED
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
