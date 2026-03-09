package cn.lgs.orbisops.domain.execution.adapter.repository;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;

import java.util.List;
import java.util.Optional;

public interface IExecutionAdapterTemplateRepository {

    List<ExecutionAdapterTemplate> list();

    Optional<ExecutionAdapterTemplate> find(String templateId);

    boolean exists(String templateId);

    ExecutionAdapterTemplate insert(ExecutionAdapterTemplate template);

    ExecutionAdapterTemplate update(ExecutionAdapterTemplate template);

    ExecutionAdapterTemplate updateStatus(
            String templateId,
            ExecutionResourceStatus status);
}
