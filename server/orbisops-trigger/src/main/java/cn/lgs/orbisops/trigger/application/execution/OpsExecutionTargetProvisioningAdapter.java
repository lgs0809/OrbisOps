package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.api.dto.OpsExecutionResourceDTO;
import cn.lgs.orbisops.application.execution.ExecutionResourceCommandApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionTargetProvisioningPort;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceDraft;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetSpecification;
import org.springframework.stereotype.Component;

@Component
public class OpsExecutionTargetProvisioningAdapter
        implements ExecutionTargetProvisioningPort<OpsExecutionResourceDTO> {

    private static final String SYSTEM_ACTOR = "system:execution-template-generation";

    private final ExecutionResourceCommandApplicationService commands;
    private final OpsExecutionResourceMapper mapper;

    public OpsExecutionTargetProvisioningAdapter(
            ExecutionResourceCommandApplicationService commands,
            OpsExecutionResourceMapper mapper) {
        if (commands == null) {
            throw new IllegalArgumentException("EXECUTION_RESOURCE_COMMANDS_REQUIRED");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("EXECUTION_RESOURCE_MAPPER_REQUIRED");
        }
        this.commands = commands;
        this.mapper = mapper;
    }

    @Override
    public OpsExecutionResourceDTO upsert(ExecutionTargetSpecification specification) {
        if (specification == null) {
            throw new IllegalArgumentException("EXECUTION_TARGET_SPECIFICATION_REQUIRED");
        }
        ExecutionResourceDraft draft = new ExecutionResourceDraft(
                specification.targetId(),
                specification.projectId(),
                specification.targetName(),
                specification.workerId(),
                specification.adapterType().code(),
                specification.templateId(),
                specification.environments(),
                specification.configuration(),
                specification.status().name());
        return mapper.dto(commands.upsert(draft, SYSTEM_ACTOR));
    }

    @Override
    public String targetId(OpsExecutionResourceDTO target) {
        return target == null ? "" : target.getResourceId();
    }
}
