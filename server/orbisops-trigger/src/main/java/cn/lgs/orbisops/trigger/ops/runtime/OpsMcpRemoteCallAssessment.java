package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed policy and audit context for one remote MCP tool call. */
public record OpsMcpRemoteCallAssessment(
        String policyId,
        String policyStatus,
        String reviewStatus,
        String effectType,
        String effectScope,
        String mutability,
        String riskLevel,
        boolean readOnly,
        String capability,
        boolean mutating,
        boolean requiresApprovedPackage,
        boolean requiresHumanApproval,
        boolean requiresDryRun,
        boolean requiresRollbackPlan,
        boolean investigateAllowed,
        boolean prepareAllowed,
        boolean landAllowed,
        String resourceEnvironment,
        OpsToolCallStage stage,
        String toolName,
        String toolId,
        boolean landingContextFieldsPresent,
        Map<String, Object> argumentPolicy) {

    public OpsMcpRemoteCallAssessment {
        resourceEnvironment = resourceEnvironment == null ? "unknown" : resourceEnvironment.trim().toLowerCase();
        argumentPolicy = argumentPolicy == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(argumentPolicy));
    }

    public boolean platformPolicyApproved() {
        return "ACTIVE".equals(policyStatus)
                && approvedReviewStatus()
                && !"UNKNOWN".equals(effectType)
                && !"UNKNOWN".equals(effectScope)
                && !"UNKNOWN".equals(mutability);
    }

    private boolean approvedReviewStatus() {
        return "HUMAN_REVIEWED".equals(reviewStatus) || "SYSTEM_VERIFIED".equals(reviewStatus);
    }

    public boolean productionResource() {
        return "prod".equals(resourceEnvironment) || "production".equals(resourceEnvironment);
    }

    public boolean callbackPolicyRequired() {
        return !landingContextFieldsPresent && !platformPolicyApproved();
    }

    public boolean approvedLandingContext() {
        return landingContextFieldsPresent && platformPolicyApproved();
    }

    public Map<String, Object> auditMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyId", policyId);
        metadata.put("policyStatus", policyStatus);
        metadata.put("reviewStatus", reviewStatus);
        metadata.put("effectType", effectType);
        metadata.put("effectScope", effectScope);
        metadata.put("mutability", mutability);
        metadata.put("riskLevel", riskLevel);
        metadata.put("readOnly", readOnly);
        metadata.put("capability", capability);
        metadata.put("mutating", mutating);
        metadata.put("requiresApprovedPackage", requiresApprovedPackage);
        metadata.put("requiresHumanApproval", requiresHumanApproval);
        metadata.put("requiresDryRun", requiresDryRun);
        metadata.put("requiresRollbackPlan", requiresRollbackPlan);
        metadata.put("investigateAllowed", investigateAllowed);
        metadata.put("prepareAllowed", prepareAllowed);
        metadata.put("landAllowed", landAllowed);
        metadata.put("resourceEnvironment", resourceEnvironment);
        metadata.put("toolCallStage", stage == null ? OpsToolCallStage.UNKNOWN.name() : stage.name());
        metadata.put("toolName", toolName);
        metadata.put("toolId", toolId);
        return Collections.unmodifiableMap(metadata);
    }
}
