package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.ApprovedLandingAgentCommand;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentRunExecutionContextCodec;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Converts an already-approved ChangePackage into one LANDING authorization. */
final class OpsLandingAgentRunAuthorizationService {

    private final OpsAgentRunExecutionContextCodec authorityCodec =
            new OpsAgentRunExecutionContextCodec();

    ApprovedLandingAgentCommand authorize(
            ChangePackageCurrent current,
            ChangePackageVersion approvedVersion,
            ChangePackageLandingPlan plan,
            ChangePackageLandingRequest request,
            String landingRunId,
            String actor) {
        if (current == null || approvedVersion == null || plan == null || request == null) {
            throw new IllegalArgumentException("LANDING_AUTHORIZATION_INPUT_REQUIRED");
        }
        Map<String, Object> snapshot = approvedVersion.snapshot().toMap();
        AgentSnapshot preparationAgent = authorityCodec.decodeAgent(snapshot.get("preparationAgentSnapshot"));
        if (preparationAgent == null) {
            throw new SecurityException("LANDING_PREPARATION_AGENT_PROVENANCE_REQUIRED");
        }
        if (!preparationAgent.agentId().equals(current.preparationAgentId())
                || preparationAgent.agentVersion() == null
                || preparationAgent.agentVersion() != current.preparationAgentVersion()) {
            throw new SecurityException("LANDING_PREPARATION_AGENT_PROVENANCE_MISMATCH");
        }
        String targetEnvironment = required(
                firstText(snapshot.get("targetEnvironment"), snapshot.get("target_environment")),
                "LANDING_TARGET_ENVIRONMENT_REQUIRED");
        String artifactDigest = artifactDigest(snapshot);
        if (requiresArtifactDigest(plan.operations()) && artifactDigest.isBlank()) {
            throw new SecurityException("LANDING_ARTIFACT_DIGEST_REQUIRED");
        }
        if (current.approvedAt() == null) {
            throw new SecurityException("LANDING_APPROVAL_TIME_REQUIRED");
        }
        Instant approvalExpiresAt = approvalAuthorityExpiry(snapshot, current.approvedAt());
        if (!approvalExpiresAt.isAfter(Instant.now())) {
            throw new SecurityException("LANDING_APPROVAL_EXPIRED");
        }
        ApprovedPackageSnapshot approvedPackage = new ApprovedPackageSnapshot(
                current.packageId(),
                approvedVersion.version(),
                approvedVersion.packageHash(),
                current.projectId(),
                targetEnvironment,
                artifactDigest,
                approvalExpiresAt);
        return new ApprovedLandingAgentCommand(
                actor,
                landingRunId,
                current,
                approvedVersion,
                plan,
                request,
                approvedPackage);
    }

    private boolean requiresArtifactDigest(List<ChangePackageLandingOperation> operations) {
        for (ChangePackageLandingOperation operation : safe(operations)) {
            Map<String, Object> raw = operation.raw() == null ? Map.of() : operation.raw();
            if (("deployment.local-java".equalsIgnoreCase(operation.toolsetId())
                    && "artifact_deploy".equalsIgnoreCase(operation.toolName()))
                    || "LOCAL_JAVA_SERVICE".equalsIgnoreCase(operation.adapterType())) {
                return true;
            }
            if (bool(raw.get("requiresArtifactDigest")) || bool(raw.get("artifactBound"))) return true;
            if (!firstText(raw.get("artifactDigest"), raw.get("artifactHash"), raw.get("digest"), raw.get("sha256")).isBlank()) {
                return true;
            }
            Object artifact = raw.get("artifact");
            if (artifact instanceof Map<?, ?> map
                    && !firstText(map.get("digest"), map.get("hash"), map.get("sha256")).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String artifactDigest(Map<String, Object> snapshot) {
        String direct = firstText(snapshot.get("artifactDigest"), snapshot.get("artifactHash"));
        if (!direct.isBlank()) return direct;
        Object artifact = snapshot.get("artifact");
        if (artifact instanceof Map<?, ?> map) {
            return firstText(map.get("digest"), map.get("hash"), map.get("sha256"));
        }
        return "";
    }

    private Instant approvalAuthorityExpiry(Map<String, Object> snapshot, Instant approvedAt) {
        String explicit = firstText(snapshot.get("approvalExpiresAt"), snapshot.get("approvalAuthorityExpiresAt"));
        if (!explicit.isBlank()) {
            try {
                return Instant.parse(explicit);
            } catch (Exception error) {
                throw new SecurityException("LANDING_APPROVAL_EXPIRY_INVALID", error);
            }
        }
        return approvedAt.plusSeconds(ApprovedPackageSnapshot.DEFAULT_APPROVAL_AUTHORITY_TTL_SECONDS);
    }

    private List<ChangePackageLandingOperation> safe(List<ChangePackageLandingOperation> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).toList();
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private String firstText(Object... values) {
        if (values == null) return "";
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String required(Object value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
