package cn.lgs.orbisops.domain.analysis.adapter.repository;

import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskIncident;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskSnapshot;

import java.util.List;
import java.util.Optional;

public interface IAnalysisTaskReadRepository {

    List<AnalysisTaskSnapshot> list(String projectId, String status, int limit);

    Optional<AnalysisTaskSnapshot> find(String projectId, String runId);

    List<AnalysisTaskIncident> findIncidents(String projectId, String runId);
}
