package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionAdministrationUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionBindingUpdateCommand;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionBindingUpdateResult;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionBindingUpdateUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCloneCommand;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCloneUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionDraftSaveCommand;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionDraftSaveUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionEvalRunCommand;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionEvalSuiteCommand;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionEvalUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecycleOperationUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionQueryUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionVersionCommand;
import cn.lgs.orbisops.application.agentdefinition.ProjectDefaultAgentBootstrapCommand;
import cn.lgs.orbisops.application.agentdefinition.ProjectDefaultAgentBootstrapResult;
import cn.lgs.orbisops.application.agentdefinition.ProjectDefaultAgentBootstrapUseCase;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentCapabilityApplicationService;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionManagementAssembly;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionViewMapper;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OpsAgentDefinitionApplicationService {

    private final OpsAgentCapabilityApplicationService capabilityApplicationService;
    private final OpsAgentDefinitionViewMapper viewMapper;
    private final ProjectDefaultAgentBootstrapUseCase<OpsAgentDefinition> defaultAgentBootstrapUseCase;
    private final AgentDefinitionEvalUseCase<
            Map<String, Object>,
            Map<String, Object>> evalUseCase;
    private final AgentDefinitionQueryUseCase<
            OpsAgentDefinition,
            Map<String, Object>> queryUseCase;
    private final AgentDefinitionAdministrationUseCase<
            OpsAgentDefinition,
            Map<String, Object>> administrationUseCase;
    private final AgentDefinitionCloneUseCase<OpsAgentDefinition> cloneUseCase;
    private final AgentDefinitionDraftSaveUseCase<OpsAgentDefinition> draftSaveUseCase;
    private final AgentDefinitionLifecycleOperationUseCase<
            OpsAgentDefinition,
            Map<String, Object>> lifecycleOperationUseCase;
    private final AgentDefinitionBindingUpdateUseCase<
            OpsAgentDefinition,
            Map<String, Object>,
            List<Map<String, Object>>> bindingUpdateUseCase;

    public OpsAgentDefinitionApplicationService(
            OpsAgentDefinitionManagementAssembly assembly) {
        if (assembly == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_MANAGEMENT_ASSEMBLY_REQUIRED");
        }
        this.capabilityApplicationService = assembly.capabilityService();
        this.viewMapper = assembly.viewMapper();
        this.defaultAgentBootstrapUseCase = assembly.defaultAgentBootstrapUseCase();
        this.evalUseCase = assembly.evalUseCase();
        this.queryUseCase = assembly.queryUseCase();
        this.administrationUseCase = assembly.administrationUseCase();
        this.cloneUseCase = assembly.cloneUseCase();
        this.draftSaveUseCase = assembly.draftSaveUseCase();
        this.lifecycleOperationUseCase = assembly.lifecycleOperationUseCase();
        this.bindingUpdateUseCase = assembly.bindingUpdateUseCase();
    }

    public List<Map<String, Object>> listAgents() {
        return queryUseCase.listProjectAgents().stream()
                .map(this::view)
                .toList();
    }

    public List<Map<String, Object>> listAgents(String projectId) {
        return queryUseCase.listProjectAgents(projectId).stream()
                .map(this::view)
                .toList();
    }

    public Map<String, Object> getAgent(String agentId) {
        OpsAgentDefinition definition = queryUseCase.find(agentId);
        return definition == null ? null : view(definition);
    }

    public Map<String, Object> saveAgent(OpsAgentDefinition definition) {
        OpsAgentDefinition saved = draftSaveUseCase.save(
                new AgentDefinitionDraftSaveCommand<>(
                        definition,
                        "save-draft-compat"));
        return view(saved);
    }

    public Map<String, Object> saveDraft(OpsAgentDefinition definition) {
        OpsAgentDefinition saved = draftSaveUseCase.save(
                new AgentDefinitionDraftSaveCommand<>(
                        definition,
                        "save-draft"));
        return view(saved);
    }

    public List<Map<String, Object>> versions(String agentId) {
        return queryUseCase.versions(agentId).stream()
                .map(this::view)
                .toList();
    }

    public Map<String, Object> validateVersion(String agentId, Integer version) {
        OpsAgentDefinition validated = lifecycleOperationUseCase.validate(
                new AgentDefinitionVersionCommand(agentId, version));
        return view(validated);
    }

    public Map<String, Object> publishVersion(String agentId, Integer version) {
        return publishVersion(agentId, version, "system");
    }

    public Map<String, Object> publishVersion(String agentId, Integer version, String actor) {
        OpsAgentDefinition current = queryUseCase.versions(agentId).stream()
                .filter(value -> value != null && version != null && version.equals(value.getVersion()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Agent version 不存在"));
        if ("PUBLISHED".equalsIgnoreCase(current.getLifecycle())) {
            return view(current);
        }
        OpsAgentDefinition validated = current;
        if ("DRAFT".equalsIgnoreCase(current.getLifecycle())) {
            validated = lifecycleOperationUseCase.validate(
                    new AgentDefinitionVersionCommand(agentId, version));
        }
        if (!"VALIDATED".equalsIgnoreCase(validated.getLifecycle())) {
            throw new IllegalStateException(
                    "AGENT_STATIC_VALIDATION_REQUIRED:actual=" + validated.getLifecycle());
        }

        Map<String, Object> suite = evalUseCase.createSuite(
                new AgentDefinitionEvalSuiteCommand<>(
                        validated.getProjectId(),
                        agentId,
                        releaseGateRequest(validated),
                        actor));
        String suiteId = String.valueOf(suite.getOrDefault("suiteId", ""));
        if (suiteId.isBlank()) {
            throw new IllegalStateException("AGENT_RELEASE_EVAL_SUITE_REQUIRED");
        }
        Map<String, Object> evaluation = evalUseCase.run(
                new AgentDefinitionEvalRunCommand(
                        validated.getProjectId(), agentId, version, suiteId, actor));
        if (!"PASSED".equalsIgnoreCase(String.valueOf(evaluation.get("status")))) {
            throw new IllegalStateException("AGENT_EVAL_GATE_NOT_PASSED：专项 Workflow 发布门禁未通过");
        }

        OpsAgentDefinition published = lifecycleOperationUseCase.publish(
                new AgentDefinitionVersionCommand(agentId, version));
        return view(published);
    }

    private Map<String, Object> releaseGateRequest(OpsAgentDefinition definition) {
        List<String> requiredNodes = definition.getNodes() == null
                ? List.of()
                : definition.getNodes().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(node -> node.getNodeId())
                        .filter(value -> value != null && !value.isBlank())
                        .toList();
        List<String> requiredRoles = definition.getAgentscopeAgents() == null
                ? List.of()
                : definition.getAgentscopeAgents().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(agent -> agent.getRole())
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList();
        Map<String, Object> evalCase = new java.util.LinkedHashMap<>();
        evalCase.put("name", requiredNodes.isEmpty()
                ? "ReAct 角色与变更边界"
                : "Workflow 结构与变更边界");
        evalCase.put("input", "release-gate");
        if (!requiredNodes.isEmpty()) {
            evalCase.put("requiredNodes", requiredNodes);
        }
        if (!requiredRoles.isEmpty()) {
            evalCase.put("requiredRoles", requiredRoles);
        }
        return Map.of(
                "projectId", definition.getProjectId(),
                "name", definition.getAgentId() + " 静态发布门禁",
                "cases", List.of(Map.copyOf(evalCase)));
    }

    public Map<String, Object> createEvalSuite(String projectId,
                                               String agentId,
                                               Map<String, Object> request,
                                               String actor) {
        return evalUseCase.createSuite(
                new AgentDefinitionEvalSuiteCommand<>(
                        projectId,
                        agentId,
                        request,
                        actor));
    }

    public Map<String, Object> runEval(String projectId,
                                      String agentId,
                                      Integer version,
                                      String suiteId,
                                      String actor) {
        return evalUseCase.run(
                new AgentDefinitionEvalRunCommand(
                        projectId,
                        agentId,
                        version,
                        suiteId,
                        actor));
    }

    public Map<String, Object> rollbackVersion(String agentId, Integer version) {
        OpsAgentDefinition published = lifecycleOperationUseCase.rollback(
                new AgentDefinitionVersionCommand(agentId, version));
        return view(published);
    }

    public boolean disableVersion(String agentId, Integer version) {
        return lifecycleOperationUseCase.disable(
                new AgentDefinitionVersionCommand(agentId, version));
    }

    public boolean deleteAgent(String agentId) {
        return administrationUseCase.delete(agentId);
    }

    public List<Map<String, Object>> reloadAgents() {
        return administrationUseCase.reload().stream()
                .map(this::view)
                .toList();
    }

    public Map<String, Object> projectAgentCapabilities(String projectId) {
        return capabilityApplicationService.projectCapabilities(projectId);
    }

    public Map<String, Object> validateBindings(OpsAgentDefinition definition) {
        return capabilityApplicationService.validate(definition);
    }

    public Map<String, Object> validateBindings(String agentId, OpsAgentDefinition definition) {
        return capabilityApplicationService.validate(agentId, definition);
    }

    public List<Map<String, Object>> agentBindings(String agentId) {
        return queryUseCase.bindings(agentId);
    }

    public Map<String, Object> updateAgentBindings(String agentId, Map<String, Object> request) {
        AgentDefinitionBindingUpdateResult<OpsAgentDefinition, List<Map<String, Object>>> result =
                bindingUpdateUseCase.update(
                        new AgentDefinitionBindingUpdateCommand<>(
                                agentId,
                                capabilityApplicationService.requestBindings(request)));
        Map<String, Object> data = view(result.definition());
        data.put("bindings", result.effectiveBindings());
        return data;
    }

    public Map<String, Object> cloneAgent(String sourceAgentId,
                                          String targetProjectId,
                                          String newAgentId,
                                          String newName) {
        OpsAgentDefinition saved = cloneUseCase.clone(
                new AgentDefinitionCloneCommand(
                        sourceAgentId,
                        targetProjectId,
                        newAgentId,
                        newName));
        return view(saved);
    }

    public Map<String, Object> createProjectDefaultAgent(String projectId, String projectName) {
        ProjectDefaultAgentBootstrapResult<OpsAgentDefinition> result =
                defaultAgentBootstrapUseCase.ensure(
                        new ProjectDefaultAgentBootstrapCommand(
                                projectId,
                                projectName,
                                "SYSTEM_DEFAULT_AGENT_BOOTSTRAP"));
        return view(result.definition());
    }

    public Map<String, Object> view(OpsAgentDefinition definition) {
        return viewMapper.view(definition);
    }

}
