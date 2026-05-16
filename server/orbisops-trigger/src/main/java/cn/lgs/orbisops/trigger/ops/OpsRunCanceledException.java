package cn.lgs.orbisops.trigger.ops;

/**
 * Signals cooperative cancellation of an operations analysis run.
 */
public class OpsRunCanceledException extends RuntimeException {

    public OpsRunCanceledException(String message) {
        super(message);
    }
}
