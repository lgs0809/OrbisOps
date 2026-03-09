package cn.lgs.orbisops.domain.worksession.run.model;

public record WorkSessionRunClaim(
        String runId,
        String projectId,
        String attemptId,
        String leaseToken,
        long fencingToken,
        long stateVersion,
        String runManifestHash) {

    public WorkSessionRunClaim {
        runId = required(runId, "WORK_SESSION_RUN_ID_REQUIRED");
        projectId = required(projectId, "WORK_SESSION_PROJECT_ID_REQUIRED");
        attemptId = required(attemptId, "WORK_SESSION_ATTEMPT_ID_REQUIRED");
        leaseToken = required(leaseToken, "WORK_SESSION_LEASE_TOKEN_REQUIRED");
        if (fencingToken <= 0L) throw new IllegalArgumentException("WORK_SESSION_FENCING_TOKEN_INVALID");
        if (stateVersion <= 0L) throw new IllegalArgumentException("WORK_SESSION_STATE_VERSION_INVALID");
        runManifestHash = required(runManifestHash, "WORK_SESSION_MANIFEST_HASH_REQUIRED");
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
