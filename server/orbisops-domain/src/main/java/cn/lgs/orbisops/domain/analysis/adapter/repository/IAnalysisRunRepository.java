package cn.lgs.orbisops.domain.analysis.adapter.repository;

import cn.lgs.orbisops.domain.analysis.model.AnalysisRunSnapshot;

import java.util.List;
import java.util.Optional;

public interface IAnalysisRunRepository {

    boolean available();

    void save(AnalysisRunSnapshot run);

    Optional<AnalysisRunSnapshot> find(String runId);

    List<AnalysisRunSnapshot> findRecent(int limit);

    int countActiveByProject(String projectId);
}
