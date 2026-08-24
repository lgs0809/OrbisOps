package cn.lgs.orbisops.domain.skill.model;

/** Authority facts loaded from a revision-bound task acceptance, never from an Agent answer. */
public record VerifiedTaskOutcome(String projectId, String runId, String taskEpisodeId,
        long revision, String verificationId, String conditionKey) {
    public boolean matches(String project, String run) {
        return project != null && project.equals(projectId) && run != null && run.equals(runId)
                && taskEpisodeId != null && !taskEpisodeId.isBlank() && revision > 0
                && verificationId != null && !verificationId.isBlank()
                && conditionKey != null && !conditionKey.isBlank();
    }
    public static VerifiedTaskOutcome unknown() { return new VerifiedTaskOutcome("", "", "", 0, "", ""); }
}
