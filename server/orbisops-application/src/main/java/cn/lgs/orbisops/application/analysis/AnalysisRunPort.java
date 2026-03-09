package cn.lgs.orbisops.application.analysis;

import java.util.List;
import java.util.Optional;

public interface AnalysisRunPort<Q, R, E> {
    R submit(Q request, String actor);
    Optional<R> get(String runId);
    List<R> list(int limit);
    boolean cancel(String runId);
    int activeCount(String projectId);
    String id(R run);
    String status(R run);
    List<E> events(String runId);
}
