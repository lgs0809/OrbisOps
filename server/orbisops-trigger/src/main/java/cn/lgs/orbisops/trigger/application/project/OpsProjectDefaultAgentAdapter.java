package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectDefaultAgentPort;
import cn.lgs.orbisops.application.project.ProjectDefaultAgentResult;
import cn.lgs.orbisops.trigger.application.ops.OpsAgentDefinitionApplicationService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsProjectDefaultAgentAdapter implements ProjectDefaultAgentPort {

    private final OpsAgentDefinitionApplicationService agentDefinitions;

    public OpsProjectDefaultAgentAdapter(OpsAgentDefinitionApplicationService agentDefinitions) {
        this.agentDefinitions = agentDefinitions;
    }

    @Override
    public ProjectDefaultAgentResult ensure(String projectId, String projectName) {
        Map<String, Object> definition = agentDefinitions.createProjectDefaultAgent(projectId, projectName);
        return new ProjectDefaultAgentResult(text(definition.get("agentId")));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
