package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.analysis.model.AnalysisRunAggregate;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunStatus;

import java.util.List;
import java.util.Optional;

public final class AnalysisRunApplicationService<Q, R, E> {

    private final AnalysisRunPort<Q, R, E> port;

    public AnalysisRunApplicationService(AnalysisRunPort<Q, R, E> port) {
        if (port == null) throw new IllegalArgumentException("ANALYSIS_RUN_PORT_REQUIRED");
        this.port = port;
    }

    public R submit(Q request, String actor) {
        if (request == null) throw new IllegalArgumentException("ANALYSIS_RUN_REQUEST_REQUIRED");
        return port.submit(request, required(actor, "ANALYSIS_RUN_ACTOR_REQUIRED"));
    }

    public Optional<R> get(String runId) {
        return port.get(required(runId, "ANALYSIS_RUN_ID_REQUIRED"));
    }

    public List<R> list(int limit) {
        return port.list(Math.max(1, Math.min(limit, 200)));
    }

    public List<E> events(String runId) {
        return port.events(required(runId, "ANALYSIS_RUN_ID_REQUIRED"));
    }

    public boolean cancel(String runId) {
        String id = required(runId, "ANALYSIS_RUN_ID_REQUIRED");
        R current = port.get(id)
                .orElseThrow(() -> new IllegalArgumentException("ANALYSIS_RUN_NOT_FOUND:" + id));
        AnalysisRunAggregate aggregate = AnalysisRunAggregate.rehydrate(
                port.id(current), AnalysisRunStatus.require(port.status(current)));
        aggregate.requireCancelable();
        return port.cancel(id);
    }

    public int activeCount(String projectId) {
        return port.activeCount(required(projectId, "ANALYSIS_PROJECT_ID_REQUIRED"));
    }

    private String required(String input, String error) {
        String value = input == null ? "" : input.trim();
        if (value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }
}
