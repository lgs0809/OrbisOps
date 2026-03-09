package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

/** External preparation capabilities that assemble a candidate package without persisting it. */
public interface ChangePackagePreparationPlanPort {

    ChangePackagePreparationPlan prepare(Map<String, Object> request, String actor);

    ChangePackagePreparationPlan prepareForSession(
            String sessionId,
            Map<String, Object> request,
            String actor);

    ChangePackageRevisionPlan revise(
            String packageId,
            Map<String, Object> request,
            String actor);

}
