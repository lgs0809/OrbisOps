package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.analysis.model.AnalysisRunStatus;

import java.time.LocalDateTime;

/** Immutable typed state for one asynchronous analysis execution. */
public record AsyncAnalysisRun<Q, R>(
        String runId,
        AnalysisRunStatus status,
        Q request,
        R response,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long durationMs) {

    public static <Q, R> AsyncAnalysisRun<Q, R> pending(
            String runId,
            Q request,
            LocalDateTime now) {
        return new AsyncAnalysisRun<>(
                runId,
                AnalysisRunStatus.PENDING,
                request,
                null,
                null,
                now,
                now,
                null);
    }

    public AsyncAnalysisRun<Q, R> running(LocalDateTime now) {
        return transition(AnalysisRunStatus.RUNNING, response, errorMessage, now, durationMs);
    }

    public AsyncAnalysisRun<Q, R> succeeded(R result, long duration, LocalDateTime now) {
        return transition(AnalysisRunStatus.SUCCEEDED, result, null, now, duration);
    }

    public AsyncAnalysisRun<Q, R> failed(String error, long duration, LocalDateTime now) {
        return transition(AnalysisRunStatus.FAILED, null, error, now, duration);
    }

    public AsyncAnalysisRun<Q, R> canceled(LocalDateTime now) {
        return transition(AnalysisRunStatus.CANCELED, null, errorMessage, now, durationMs);
    }

    public boolean terminal() {
        return status != null && status.terminal();
    }

    private AsyncAnalysisRun<Q, R> transition(
            AnalysisRunStatus target,
            R resolvedResponse,
            String resolvedError,
            LocalDateTime now,
            Long resolvedDuration) {
        return new AsyncAnalysisRun<>(
                runId,
                target,
                request,
                resolvedResponse,
                resolvedError,
                createdAt,
                now,
                resolvedDuration);
    }
}
