package cn.lgs.orbisops.trigger.ops.runtime;

/**
 * Signals that authoritative runtime journaling/checkpoint persistence failed.
 * A completed model call must not be reclassified as model degradation merely
 * because its durable runtime fact could not be recorded.
 */
public final class OpsRuntimePersistenceException extends IllegalStateException {

    public OpsRuntimePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
