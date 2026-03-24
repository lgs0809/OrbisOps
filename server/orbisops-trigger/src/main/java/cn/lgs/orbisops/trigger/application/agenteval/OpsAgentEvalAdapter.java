package cn.lgs.orbisops.trigger.application.agenteval;

import cn.lgs.orbisops.application.agenteval.AgentEvalApplicationService;
import cn.lgs.orbisops.application.agenteval.AgentEvalCreateSuiteCommand;
import cn.lgs.orbisops.application.agentdefinition.ProjectDefaultAgentEvalPort;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsAgentEvalAdapter implements ProjectDefaultAgentEvalPort {

    private final AgentEvalApplicationService application;
    private final OpsAgentEvalMapper mapper;

    public OpsAgentEvalAdapter(
            AgentEvalApplicationService application,
            OpsAgentEvalMapper mapper) {
        this.application = application;
        this.mapper = mapper;
    }

    public Map<String, Object> createSuite(
            String projectId,
            String agentId,
            Map<String, Object> request,
            String actor) {
        return mapper.suiteView(application.createSuite(
                mapper.createSuiteCommand(projectId, agentId, request, actor)));
    }

    public Map<String, Object> run(
            String projectId,
            String agentId,
            int version,
            String suiteId,
            String actor) {
        return mapper.runView(application.run(projectId, agentId, version, suiteId, actor));
    }

    @Override
    public AgentEvalSuite createReleaseSuite(AgentEvalCreateSuiteCommand command) {
        return application.createSuite(command);
    }

    @Override
    public AgentEvalRunResult runReleaseEvaluation(
            String projectId,
            String agentId,
            int version,
            String suiteId,
            String actor) {
        return application.run(projectId, agentId, version, suiteId, actor);
    }

    @Override
    public void assertReleaseAllowed(
            String projectId,
            String agentId,
            int version,
            String definitionHash) {
        application.assertReleaseAllowed(
                projectId,
                agentId,
                version,
                definitionHash);
    }

    public void assertReleaseAllowed(OpsAgentDefinition definition) {
        if (definition == null) throw new IllegalStateException("AGENT_RELEASE_DEFINITION_NOT_PINNED");
        assertReleaseAllowed(
                definition.getProjectId(),
                definition.getAgentId(),
                definition.getVersion(),
                definition.getDefinitionHash());
    }
}
