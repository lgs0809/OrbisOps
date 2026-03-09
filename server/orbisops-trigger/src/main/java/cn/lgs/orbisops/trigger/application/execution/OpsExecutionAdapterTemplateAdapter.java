package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.api.dto.OpsExecutionResourceDTO;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTargetGenerationApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateCatalogApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateCommands;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplatePort;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Thin composition adapter over typed execution-template application services. */
@Component
public class OpsExecutionAdapterTemplateAdapter
        implements ExecutionAdapterTemplatePort<OpsExecutionResourceDTO> {

    private final ExecutionAdapterTemplateCatalogApplicationService catalogService;
    private final ExecutionAdapterTargetGenerationApplicationService<OpsExecutionResourceDTO>
            targetGenerationService;
    private final OpsExecutionTargetGenerationMapper targetGenerationMapper;

    public OpsExecutionAdapterTemplateAdapter(
            ExecutionAdapterTemplateCatalogApplicationService catalogService,
            ExecutionAdapterTargetGenerationApplicationService<OpsExecutionResourceDTO>
                    targetGenerationService,
            OpsExecutionTargetGenerationMapper targetGenerationMapper) {
        if (catalogService == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_CATALOG_SERVICE_REQUIRED");
        if (targetGenerationService == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_TARGET_SERVICE_REQUIRED");
        if (targetGenerationMapper == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_TARGET_MAPPER_REQUIRED");
        this.catalogService = catalogService;
        this.targetGenerationService = targetGenerationService;
        this.targetGenerationMapper = targetGenerationMapper;
    }

    @Override
    public List<ExecutionAdapterTemplate> listTemplates() {
        return catalogService.listTemplates();
    }

    @Override
    public ExecutionAdapterTemplate getTemplate(String templateId) {
        return catalogService.getTemplate(templateId);
    }

    @Override
    public ExecutionAdapterTemplate createTemplate(
            ExecutionAdapterTemplateCommands.Mutation command) {
        return catalogService.createTemplate(command);
    }

    @Override
    public ExecutionAdapterTemplate updateTemplate(
            String templateId,
            ExecutionAdapterTemplateCommands.Mutation command) {
        return catalogService.updateTemplate(templateId, command);
    }

    @Override
    public ExecutionAdapterTemplate updateTemplateStatus(
            String templateId,
            ExecutionResourceStatus status) {
        return catalogService.updateTemplateStatus(templateId, status);
    }

    @Override
    public ExecutionAdapterTemplate copyTemplate(
            String templateId,
            ExecutionAdapterTemplateCommands.Mutation command) {
        return catalogService.copyTemplate(templateId, command);
    }

    @Override
    public List<Map<String, Object>> generatedTargets(String templateId) {
        return catalogService.generatedTargets(templateId);
    }

    @Override
    public OpsExecutionResourceDTO generateTarget(
            String projectId,
            String templateId,
            Map<String, Object> request) {
        return targetGenerationService.generate(
                targetGenerationMapper.input(projectId, templateId, request));
    }

    @Override
    public String targetId(OpsExecutionResourceDTO target) {
        return targetGenerationService.targetId(target);
    }
}
