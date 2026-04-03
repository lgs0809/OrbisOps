package cn.lgs.orbisops.application.modelpolicy;

import cn.lgs.orbisops.domain.modelpolicy.model.ModelDefaultPolicy;

import java.util.Optional;

/** Typed persistence boundary for default-model policy facts. */
public interface ModelDefaultPolicyPort {

    Optional<ModelDefaultPolicySnapshot> find(String projectId);

    ModelDefaultPolicySnapshot save(ModelDefaultPolicy policy);
}
