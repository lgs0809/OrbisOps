package cn.lgs.orbisops.application.skill;

/** Read-only task details, never raw provider payloads or execution authority. */
public interface SkillEvolutionDiagnosticPort {
    String currentFailure(String projectId, String jobId, int attempt);
    /** A verified authored conclusion can exist without a publishable change or candidate. */
    record AuthoredDecision(String planId, String operation, String reason, String source,
                            String model, boolean currentSource) { }
    default java.util.Optional<AuthoredDecision> authoredDecision(String projectId, String jobId, String sourceId) {
        return java.util.Optional.empty();
    }
    default java.util.Map<String,Object> savedExperience(String projectId, String jobId, String sourceId) {
        return java.util.Map.of();
    }
    /** A failed author job can still have an immutable proposal and a separately settled release. */
    default java.util.Map<String,Object> authoredPublication(String projectId, String jobId, String sourceId) {
        return java.util.Map.of();
    }
    /** Current publication facts are a projection; the generated patch stays immutable. */
    record PublicationRef(String projectId, String candidateId) { }
    record PublicationState(String status, String operation, java.util.List<String> targetSkillIds,
                            int releasedVersion) { }
    default java.util.Map<PublicationRef, PublicationState> publications(java.util.Collection<PublicationRef> refs) {
        return java.util.Map.of();
    }
    SkillEvolutionDiagnosticPort NONE = (projectId, jobId, attempt) -> "";
}
