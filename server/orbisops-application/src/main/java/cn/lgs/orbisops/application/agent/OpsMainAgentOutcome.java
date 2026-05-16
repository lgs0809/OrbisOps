package cn.lgs.orbisops.application.agent;

import java.util.Map;

public record OpsMainAgentOutcome(
        OpsActionStatus status,
        Object result,
        OpsFailureDescriptor failure,
        Map<String, Object> metadata) {

    public OpsMainAgentOutcome {
        status = status == null ? OpsActionStatus.FAILED : status;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static OpsMainAgentOutcome succeeded(Object result, Map<String, Object> metadata) {
        return new OpsMainAgentOutcome(OpsActionStatus.SUCCEEDED, result, null, metadata);
    }

    public static OpsMainAgentOutcome withResult(OpsActionStatus status,
                                                 Object result,
                                                 OpsFailureDescriptor failure,
                                                 Map<String, Object> metadata) {
        return new OpsMainAgentOutcome(status, result, failure, metadata);
    }

    public static OpsMainAgentOutcome failed(OpsActionStatus status, OpsFailureDescriptor failure) {
        return new OpsMainAgentOutcome(status, null, failure, Map.of());
    }

    public boolean isSuccessful() {
        return status == OpsActionStatus.SUCCEEDED || status == OpsActionStatus.ASYNC_ACCEPTED;
    }
}
