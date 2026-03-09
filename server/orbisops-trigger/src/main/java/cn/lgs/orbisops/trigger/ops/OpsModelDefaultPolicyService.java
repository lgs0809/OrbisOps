package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Compatibility facade that delegates all validation and persistence to Application. */
@Service
public class OpsModelDefaultPolicyService {

    private final ModelDefaultPolicyApplicationService policies;

    public OpsModelDefaultPolicyService(ModelDefaultPolicyApplicationService policies) {
        if (policies == null) throw new IllegalArgumentException("MODEL_DEFAULT_POLICY_APPLICATION_SERVICE_REQUIRED");
        this.policies = policies;
    }

    public Map<String, Object> get(String projectId) {
        return policies.get(projectId);
    }

    public Map<String, Object> update(
            String projectId,
            Map<String, Object> request,
            String actor) {
        return policies.update(projectId, request, actor);
    }
}
