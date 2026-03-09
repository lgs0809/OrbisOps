package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;

import java.util.List;

public interface ChangePackageLandingJournalPort {

    boolean available();

    void initialize(String landingRunId, ChangePackageLandingPlan plan);

    void completeFromRuntimeResult(String landingRunId,
                                   ChangePackageLandingPlan plan,
                                   ChangePackageLandingRuntimeResult result);

    List<LandingOperationFact> operationFacts(String landingRunId);

    void markAllUnexecutedBlocked(String landingRunId, String reasonCode, Object result);
}
