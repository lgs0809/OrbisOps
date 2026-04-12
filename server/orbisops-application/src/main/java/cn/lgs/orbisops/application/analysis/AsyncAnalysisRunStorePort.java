package cn.lgs.orbisops.application.analysis;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for asynchronous analysis run state. */
public interface AsyncAnalysisRunStorePort<Q, R> {

    void save(AsyncAnalysisRun<Q, R> run);

    Optional<AsyncAnalysisRun<Q, R>> get(String runId);

    List<AsyncAnalysisRun<Q, R>> list(int limit);

    int activeCountByProject(String projectId);
}
