package cn.lgs.orbisops.trigger.ops;

/** Shared lifecycle status vocabulary for asynchronous analysis runs. */
final class OpsAnalysisRunStatus {

    static final String PENDING = "PENDING";
    static final String RUNNING = "RUNNING";
    static final String SUCCEEDED = "SUCCEEDED";
    static final String FAILED = "FAILED";
    static final String CANCELED = "CANCELED";

    private OpsAnalysisRunStatus() {
    }

    static boolean terminal(String status) {
        return SUCCEEDED.equals(status) || FAILED.equals(status) || CANCELED.equals(status);
    }
}
