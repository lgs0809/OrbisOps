package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OpsAgentCapabilityApplicationService {

    private final OpsAgentCapabilityCatalogService catalogService;
    private final OpsAgentCapabilityBindingPolicy bindingPolicy;
    private final OpsAgentCapabilityBindingEditor bindingEditor;
    private final OpsAgentCapabilityBindingMapper bindingViewMapper;

    public OpsAgentCapabilityApplicationService(OpsAgentCapabilityCatalogService catalogService,
                                                OpsAgentCapabilityBindingPolicy bindingPolicy,
                                                OpsAgentCapabilityBindingEditor bindingEditor) {
        this.catalogService = catalogService;
        this.bindingPolicy = bindingPolicy;
        this.bindingEditor = bindingEditor;
        this.bindingViewMapper = new OpsAgentCapabilityBindingMapper();
    }

    public Map<String, Object> projectCapabilities(String projectId) {
        return catalogService.projectCapabilities(projectId);
    }

    public Map<String, Object> validate(OpsAgentDefinition definition) {
        return bindingPolicy.validate(definition);
    }

    public Map<String, Object> validate(String agentId, OpsAgentDefinition definition) {
        return bindingPolicy.validate(agentId, definition);
    }

    public void assertValid(OpsAgentDefinition definition) {
        bindingPolicy.assertValid(definition);
    }

    public List<Map<String, Object>> requestBindings(Map<String, Object> request) {
        return bindingEditor.requestBindings(request);
    }

    public List<Map<String, Object>> extractBindings(OpsAgentDefinition definition) {
        return bindingViewMapper.compatibilityViews(bindingViewMapper.map(definition).bindings().stream()
                .filter(binding -> binding.capabilityType() != AgentCapabilityType.INLINE_MCP_SERVER)
                .toList());
    }

    public void applyBindings(OpsAgentDefinition definition, List<Map<String, Object>> bindings) {
        bindingEditor.applyBindings(definition, bindings);
    }

    public void sanitizeForProject(OpsAgentDefinition definition, String projectId) {
        bindingPolicy.sanitizeForProject(definition, projectId);
    }

    public String requireExistingProject(String projectId) {
        return bindingPolicy.requireExistingProject(projectId);
    }
}
