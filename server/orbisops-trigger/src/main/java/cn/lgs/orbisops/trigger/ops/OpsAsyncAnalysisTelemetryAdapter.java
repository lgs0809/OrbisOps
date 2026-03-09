package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.analysis.AsyncAnalysisTelemetryPort;

/** Telemetry adapter for asynchronous analysis run lifecycle. */
public final class OpsAsyncAnalysisTelemetryAdapter implements AsyncAnalysisTelemetryPort {

    private final OpsTelemetryService telemetry;

    public OpsAsyncAnalysisTelemetryAdapter(OpsTelemetryService telemetry) {
        if (telemetry == null) {
            throw new IllegalArgumentException("OPS_TELEMETRY_SERVICE_REQUIRED");
        }
        this.telemetry = telemetry;
    }

    @Override public void submitted() { telemetry.recordSubmitted(); }
    @Override public void started() { telemetry.recordStarted(); }
    @Override public void succeeded(long durationMs) { telemetry.recordSucceeded(durationMs); }
    @Override public void failed(long durationMs) { telemetry.recordFailed(durationMs); }
    @Override public void canceled() { telemetry.recordCanceled(); }
}
