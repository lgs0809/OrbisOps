package cn.lgs.orbisops.application.analysis;

/** Telemetry boundary for asynchronous analysis lifecycle counters and durations. */
public interface AsyncAnalysisTelemetryPort {

    void submitted();

    void started();

    void succeeded(long durationMs);

    void failed(long durationMs);

    void canceled();
}
