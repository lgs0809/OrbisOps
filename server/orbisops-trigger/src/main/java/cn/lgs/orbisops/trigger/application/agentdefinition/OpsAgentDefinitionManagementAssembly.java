package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionAdministrationUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionBindingUpdateUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCloneUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionDraftSaveUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionEvalUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecycleOperationUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecycleUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionQueryUseCase;
import cn.lgs.orbisops.application.agentdefinition.ProjectDefaultAgentBootstrapUseCase;
import cn.lgs.orbisops.trigger.application.agenteval.OpsAgentEvalAdapter;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.List;
import java.util.Map;

/** Immutable management object graph assembled by the Spring composition root or tests. */
public record OpsAgentDefinitionManagementAssembly(
        OpsAgentCapabilityApplicationService capabilityService,
        OpsAgentDefinitionViewMapper viewMapper,
        ProjectDefaultAgentBootstrapUseCase<OpsAgentDefinition> defaultAgentBootstrapUseCase,
        AgentDefinitionEvalUseCase<Map<String, Object>, Map<String, Object>> evalUseCase,
        AgentDefinitionQueryUseCase<OpsAgentDefinition, Map<String, Object>> queryUseCase,
        AgentDefinitionAdministrationUseCase<OpsAgentDefinition, Map<String, Object>> administrationUseCase,
        AgentDefinitionCloneUseCase<OpsAgentDefinition> cloneUseCase,
        AgentDefinitionDraftSaveUseCase<OpsAgentDefinition> draftSaveUseCase,
        AgentDefinitionLifecycleOperationUseCase<OpsAgentDefinition, Map<String, Object>> lifecycleOperationUseCase,
        AgentDefinitionBindingUpdateUseCase<
                OpsAgentDefinition,
                Map<String, Object>,
                List<Map<String, Object>>> bindingUpdateUseCase) {

    public OpsAgentDefinitionManagementAssembly {
        if (capabilityService == null
                || viewMapper == null
                || defaultAgentBootstrapUseCase == null
                || evalUseCase == null
                || queryUseCase == null
                || administrationUseCase == null
                || cloneUseCase == null
                || draftSaveUseCase == null
                || lifecycleOperationUseCase == null
                || bindingUpdateUseCase == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_MANAGEMENT_ASSEMBLY_INCOMPLETE");
        }
    }

    public static OpsAgentDefinitionManagementAssembly create(
            OpsAgentDefinitionGateway definitionGateway,
            AgentDefinitionLifecycleUseCase<OpsAgentDefinition> lifecycleUseCase,
            OpsConfigAuditService auditService,
            OpsAgentCapabilityApplicationService capabilityService,
            OpsAgentEvalAdapter evalAdapter) {
        if (definitionGateway == null
                || lifecycleUseCase == null
                || auditService == null
                || capabilityService == null
                || evalAdapter == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_MANAGEMENT_DEPENDENCIES_REQUIRED");
        }
        OpsAgentDefinitionExecutionShapeMapper shapeMapper =
                new OpsAgentDefinitionExecutionShapeMapper();
        OpsAgentDefinitionViewMapper viewMapper = new OpsAgentDefinitionViewMapper();

        ProjectDefaultAgentBootstrapUseCase<OpsAgentDefinition> bootstrap =
                new ProjectDefaultAgentBootstrapUseCase<>(
                        lifecycleUseCase,
                        new OpsProjectDefaultAgentDefinitionAdapter(
                                definitionGateway,
                                capabilityService,
                                shapeMapper),
                        evalAdapter,
                        new OpsProjectDefaultAgentAuditAdapter(auditService, viewMapper));

        AgentDefinitionEvalUseCase<Map<String, Object>, Map<String, Object>> eval =
                new AgentDefinitionEvalUseCase<>(
                        new OpsAgentDefinitionEvalAdapter(capabilityService, evalAdapter));
        AgentDefinitionQueryUseCase<OpsAgentDefinition, Map<String, Object>> query =
                new AgentDefinitionQueryUseCase<>(
                        new OpsAgentDefinitionQueryAdapter(definitionGateway, capabilityService));

        OpsAgentDefinitionAdministrationAdapter administrationAdapter =
                new OpsAgentDefinitionAdministrationAdapter(
                        definitionGateway,
                        viewMapper,
                        auditService);
        AgentDefinitionAdministrationUseCase<OpsAgentDefinition, Map<String, Object>> administration =
                new AgentDefinitionAdministrationUseCase<>(
                        administrationAdapter,
                        administrationAdapter);

        OpsAgentDefinitionDraftSaveAdapter draftAdapter =
                new OpsAgentDefinitionDraftSaveAdapter(
                        capabilityService,
                        shapeMapper,
                        auditService);
        AgentDefinitionDraftSaveUseCase<OpsAgentDefinition> draft =
                new AgentDefinitionDraftSaveUseCase<>(
                        lifecycleUseCase,
                        draftAdapter,
                        draftAdapter);

        OpsAgentDefinitionLifecycleOperationAdapter lifecycleAdapter =
                new OpsAgentDefinitionLifecycleOperationAdapter(
                        definitionGateway,
                        viewMapper,
                        auditService);
        AgentDefinitionLifecycleOperationUseCase<OpsAgentDefinition, Map<String, Object>> lifecycle =
                new AgentDefinitionLifecycleOperationUseCase<>(
                        lifecycleUseCase,
                        lifecycleAdapter,
                        lifecycleAdapter);

        OpsAgentDefinitionCloneAdapter cloneAdapter =
                new OpsAgentDefinitionCloneAdapter(
                        definitionGateway,
                        capabilityService,
                        shapeMapper,
                        auditService);
        AgentDefinitionCloneUseCase<OpsAgentDefinition> clone =
                new AgentDefinitionCloneUseCase<>(
                        lifecycleUseCase,
                        cloneAdapter,
                        cloneAdapter);

        OpsAgentDefinitionBindingUpdateAdapter bindingAdapter =
                new OpsAgentDefinitionBindingUpdateAdapter(
                        definitionGateway,
                        capabilityService,
                        shapeMapper,
                        auditService);
        AgentDefinitionBindingUpdateUseCase<
                OpsAgentDefinition,
                Map<String, Object>,
                List<Map<String, Object>>> bindingUpdate =
                new AgentDefinitionBindingUpdateUseCase<>(
                        lifecycleUseCase,
                        bindingAdapter,
                        bindingAdapter);

        return new OpsAgentDefinitionManagementAssembly(
                capabilityService,
                viewMapper,
                bootstrap,
                eval,
                query,
                administration,
                clone,
                draft,
                lifecycle,
                bindingUpdate);
    }
}
