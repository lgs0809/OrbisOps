package cn.lgs.orbisops.domain.worksession.runtime.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Trusted, immutable authority context for one recoverable Agent run. */
public record AgentRunExecutionContext(
        String runId,
        String workSessionId,
        String projectId,
        TriggerSource triggerSource,
        AgentExecutionStyle executionStyle,
        AgentExecutionStage stage,
        AgentSnapshot agentSnapshot,
        Optional<ApprovedPackageSnapshot> approvedPackage,
        CapabilityProfile capabilityProfile,
        Set<String> allowedResourceIds,
        Set<String> allowedToolIds,
        Instant deadline) {

    public AgentRunExecutionContext {
        runId = required(runId, "RUN_ID_REQUIRED");
        workSessionId = required(workSessionId, "WORK_SESSION_ID_REQUIRED");
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        triggerSource = triggerSource == null ? TriggerSource.API : triggerSource;
        executionStyle = executionStyle == null ? AgentExecutionStyle.REACT : executionStyle;
        stage = stage == null ? AgentExecutionStage.INVESTIGATE : stage;
        if (agentSnapshot == null) throw new IllegalArgumentException("AGENT_SNAPSHOT_REQUIRED");
        approvedPackage = approvedPackage == null ? Optional.empty() : approvedPackage;
        capabilityProfile = capabilityProfile == null
                ? CapabilityProfile.PROD_DIAGNOSTIC
                : capabilityProfile;
        allowedResourceIds = copy(allowedResourceIds);
        allowedToolIds = copy(allowedToolIds);
        deadline = deadline == null ? Instant.now().plusSeconds(1800) : deadline;
        if (stage == AgentExecutionStage.LANDING && approvedPackage.isEmpty()) {
            throw new IllegalArgumentException("LANDING_APPROVED_PACKAGE_REQUIRED");
        }
        if (stage != AgentExecutionStage.LANDING && approvedPackage.isPresent()) {
            throw new IllegalArgumentException("APPROVED_PACKAGE_ONLY_ALLOWED_FOR_LANDING");
        }
        if (stage == AgentExecutionStage.LANDING
                && capabilityProfile != CapabilityProfile.PROD_FULL) {
            throw new IllegalArgumentException("LANDING_PROFILE_REQUIRED");
        }
        if (stage == AgentExecutionStage.LANDING && executionStyle != AgentExecutionStyle.REACT) {
            throw new IllegalArgumentException("LANDING_REACT_STYLE_REQUIRED");
        }
        if (stage != AgentExecutionStage.LANDING
                && capabilityProfile == CapabilityProfile.PROD_FULL) {
            throw new IllegalArgumentException("PROD_FULL_PROFILE_FORBIDDEN");
        }
    }

    public boolean expired(Instant now) {
        Instant instant = now == null ? Instant.now() : now;
        return !deadline.isAfter(instant);
    }

    private static Set<String> copy(Set<String> source) {
        if (source == null || source.isEmpty()) return Set.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        source.stream().map(AgentRunExecutionContext::text)
                .filter(value -> !value.isBlank()).forEach(values::add);
        return Set.copyOf(values);
    }

    private static String required(Object value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
