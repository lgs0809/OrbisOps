package cn.lgs.orbisops.domain.skill.model;

import java.util.Map;

public record SkillRuntimeCandidate(String skillId,
                                    String projectId,
                                    String scope,
                                    String name,
                                    String description,
                                    int version,
                                    String skillHash,
                                    String packageHash,
                                    String manifestHash,
                                    Map<String, String> artifactHashes,
                                    String entrypoint,
                                    String status,
                                    String updateMode,
                                    int contentLength,
                                    SkillRoutingProfile routingProfile,
                                    SkillGovernanceState governanceState) {

    public SkillRuntimeCandidate {
        skillId = safe(skillId);
        projectId = safe(projectId);
        scope = safe(scope);
        name = safe(name);
        description = safe(description);
        skillHash = safe(skillHash);
        packageHash = safe(packageHash);
        manifestHash = safe(manifestHash);
        artifactHashes = artifactHashes == null ? Map.of() : Map.copyOf(artifactHashes);
        entrypoint = safe(entrypoint);
        status = safe(status);
        updateMode = safe(updateMode);
        routingProfile = routingProfile == null
                ? SkillRoutingProfile.empty(description) : routingProfile;
        governanceState = governanceState == null
                ? SkillGovernanceState.fromLegacy(status, updateMode, "", "", null)
                : governanceState;
        if (skillId.isBlank() || version <= 0 || skillHash.isBlank()) {
            throw new IllegalArgumentException("SKILL_RUNTIME_CANDIDATE_IDENTITY_REQUIRED");
        }
    }

    public SkillRuntimeCandidate(String skillId,
                                 String projectId,
                                 String scope,
                                 String name,
                                 String description,
                                 int version,
                                 String skillHash,
                                 String packageHash,
                                 String manifestHash,
                                 Map<String, String> artifactHashes,
                                 String entrypoint,
                                 String status,
                                 String updateMode,
                                 int contentLength) {
        this(skillId, projectId, scope, name, description, version, skillHash,
                packageHash, manifestHash, artifactHashes, entrypoint, status,
                updateMode, contentLength, SkillRoutingProfile.empty(description), null);
    }

    public SkillRuntimeCandidate(String skillId,
                                 String projectId,
                                 String scope,
                                 String name,
                                 String description,
                                 int version,
                                 String skillHash,
                                 String packageHash,
                                 String manifestHash,
                                 Map<String, String> artifactHashes,
                                 String entrypoint,
                                 String status,
                                 String updateMode,
                                 int contentLength,
                                 SkillRoutingProfile routingProfile) {
        this(skillId, projectId, scope, name, description, version, skillHash,
                packageHash, manifestHash, artifactHashes, entrypoint, status,
                updateMode, contentLength, routingProfile, null);
    }

    public String descriptor() {
        return String.join(" ", skillId, name, description,
                routingProfile.positiveText(), routingProfile.negativeText()).trim();
    }

    public String retrievalDescriptor() {
        return String.join(" ", skillId, name, routingProfile.positiveText()).trim();
    }

    public String exclusionDescriptor() {
        return routingProfile.negativeText();
    }

    public boolean activeAtUse() {
        return governanceState.activeAtUse();
    }

    public boolean routingReady() {
        return routingProfile != null
                && !routingProfile.useCases().isEmpty()
                && !routingProfile.exclusions().isEmpty();
    }

    public static boolean activeAtUse(String status, String updateMode) {
        return SkillGovernanceState.fromLegacy(status, updateMode, "", "", null).activeAtUse();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
