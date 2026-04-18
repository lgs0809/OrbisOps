package cn.lgs.orbisops.application.changepackage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChangePackageCommands {

    private ChangePackageCommands() {
    }

    public record Prepare(Map<String, Object> request, String actor) {
        public Prepare {
            request = copy(request);
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }
    }

    public record PrepareForSession(String sessionId, Map<String, Object> request, String actor) {
        public PrepareForSession {
            sessionId = required(sessionId, "CHANGE_PACKAGE_SESSION_ID_REQUIRED");
            request = copy(request);
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }
    }

    public record Revise(String packageId, Map<String, Object> request, String actor) {
        public Revise {
            packageId = normalizePackageId(packageId);
            request = copy(request);
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }
    }

    public record Validate(String packageId, int version, String actor) {
        public Validate {
            packageId = normalizePackageId(packageId);
            if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_INVALID");
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }
    }

    public record ValidationWriteback(String packageId,
                                      boolean passed,
                                      ChangePackageValidationReport validationReport,
                                      String actor) {
        public ValidationWriteback {
            packageId = normalizePackageId(packageId);
            validationReport = validationReport == null
                    ? ChangePackageValidationReport.from(Map.of())
                    : validationReport;
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }

        public ValidationWriteback(String packageId,
                                   boolean passed,
                                   Map<String, Object> validationReport,
                                   String actor) {
            this(packageId, passed, ChangePackageValidationReport.from(validationReport), actor);
        }
    }

    public record SubmitReview(String packageId, ChangePackageReviewRequest request, String actor) {
        public SubmitReview {
            packageId = normalizePackageId(packageId);
            request = request == null ? ChangePackageReviewRequest.from(Map.of()) : request;
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }

        public SubmitReview(String packageId, Map<String, Object> request, String actor) {
            this(packageId, ChangePackageReviewRequest.from(request), actor);
        }
    }

    public record Approve(String packageId,
                          int version,
                          String packageHash,
                          String actor,
                          ChangePackageApprovalContext approvalContext) {
        public Approve {
            packageId = normalizePackageId(packageId);
            if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_INVALID");
            packageHash = normalizeText(packageHash);
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
            approvalContext = approvalContext == null
                    ? ChangePackageApprovalContext.from(Map.of())
                    : approvalContext;
        }

        public Approve(String packageId,
                       int version,
                       String packageHash,
                       String actor,
                       Map<String, Object> approvalContext) {
            this(packageId, version, packageHash, actor, ChangePackageApprovalContext.from(approvalContext));
        }
    }

    public record Reject(String packageId, ChangePackageRejectionRequest request, String actor) {
        public Reject {
            packageId = normalizePackageId(packageId);
            request = request == null ? ChangePackageRejectionRequest.from(Map.of()) : request;
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }

        public Reject(String packageId, Map<String, Object> request, String actor) {
            this(packageId, ChangePackageRejectionRequest.from(request), actor);
        }
    }

    public record Land(String packageId, Map<String, Object> request, String actor) {
        public Land {
            packageId = normalizePackageId(packageId);
            request = copy(request);
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }
    }

    public record Cleanup(String packageId, ChangePackageCleanupRequest request, String actor) {
        public Cleanup {
            packageId = normalizePackageId(packageId);
            request = request == null ? ChangePackageCleanupRequest.from(Map.of()) : request;
            actor = required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        }

        public Cleanup(String packageId, Map<String, Object> request, String actor) {
            this(packageId, ChangePackageCleanupRequest.from(request), actor);
        }
    }

    private static String normalizePackageId(String value) {
        return required(value, "CHANGE_PACKAGE_ID_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
