package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionTargetSpecification;

public interface ExecutionTargetProvisioningPort<T> {

    T upsert(ExecutionTargetSpecification specification);

    String targetId(T target);
}
