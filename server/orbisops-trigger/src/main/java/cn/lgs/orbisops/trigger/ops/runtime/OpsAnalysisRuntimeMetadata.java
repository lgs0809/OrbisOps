package cn.lgs.orbisops.trigger.ops.runtime;

/** Canonical metadata keys shared by Analysis entry adapters and runtime coordinators. */
public final class OpsAnalysisRuntimeMetadata {

    public static final String REQUEST_KEY = "opsAnalysisRequest";
    public static final String RESPONSE_KEY = "opsAnalysisResponse";
    public static final String QUESTION_CONTEXT_KEY = "opsAnalysisQuestionContext";

    private OpsAnalysisRuntimeMetadata() {
    }
}
