package cn.lgs.orbisops.trigger.ops.runtime;

/** Internal metadata keys shared by Work Session context boundaries. */
final class OpsWorkSessionContextMetadataKeys {

    static final String RUNTIME_MEMORY_CONTEXT = "_runtimeMemoryContext";
    static final String ORIGINAL_USER_QUERY = "_originalUserQuery";
    static final String REWRITTEN_QUERY = "_rewrittenQuery";

    private OpsWorkSessionContextMetadataKeys() {
    }
}
