package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionAdapterTemplateRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetGenerationInput;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetSpecification;
import cn.lgs.orbisops.domain.execution.service.ExecutionTargetGenerationPolicy;

public final class ExecutionAdapterTargetGenerationApplicationService<T> {

    private final IExecutionAdapterTemplateRepository repository;
    private final ExecutionTargetProvisioningPort<T> provisioningPort;
    private final ExecutionTargetGenerationPolicy policy;

    public ExecutionAdapterTargetGenerationApplicationService(
            IExecutionAdapterTemplateRepository repository,
            ExecutionTargetProvisioningPort<T> provisioningPort) {
        this(repository, provisioningPort, new ExecutionTargetGenerationPolicy());
    }

    ExecutionAdapterTargetGenerationApplicationService(
            IExecutionAdapterTemplateRepository repository,
            ExecutionTargetProvisioningPort<T> provisioningPort,
            ExecutionTargetGenerationPolicy policy) {
        if (repository == null) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_REPOSITORY_REQUIRED");
        }
        if (provisioningPort == null) {
            throw new IllegalArgumentException("EXECUTION_TARGET_PROVISIONING_PORT_REQUIRED");
        }
        if (policy == null) {
            throw new IllegalArgumentException("EXECUTION_TARGET_GENERATION_POLICY_REQUIRED");
        }
        this.repository = repository;
        this.provisioningPort = provisioningPort;
        this.policy = policy;
    }

    public T generate(ExecutionTargetGenerationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("EXECUTION_TARGET_GENERATION_INPUT_REQUIRED");
        }
        ExecutionAdapterTemplate template = repository.find(input.templateId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "执行适配器模板不存在：" + input.templateId()));
        ExecutionTargetSpecification specification = policy.generate(template, input);
        return provisioningPort.upsert(specification);
    }

    public String targetId(T target) {
        return target == null ? "" : provisioningPort.targetId(target);
    }
}
