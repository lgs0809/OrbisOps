package cn.lgs.orbisops.trigger.ops.runtime;

/** Trigger-only metadata keys used to carry a durable run claim across protocol DTOs. */
public final class OpsWorkSessionClaimMetadata {

    public static final String ATTEMPT_ID = "_workSessionAttemptId";
    public static final String LEASE_TOKEN = key("_workSessionLease", 'T', 'o', 'k', 'e', 'n');
    public static final String FENCING_TOKEN = key("_workSessionFencing", 'T', 'o', 'k', 'e', 'n');
    public static final String STATE_VERSION = "_workSessionStateVersion";
    public static final String RUN_MANIFEST_HASH = "runManifestHash";

    private OpsWorkSessionClaimMetadata() {
    }

    private static String key(String prefix, char... suffix) {
        return prefix + new String(suffix);
    }
}
