package cn.lgs.orbisops.trigger.application.agenteval;

import cn.lgs.orbisops.application.agenteval.AgentEvalCreateSuiteCommand;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseExecution;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalDefinitionSnapshot;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalEdge;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalNode;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsGraphEdge;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsAgentEvalMapper {

    public AgentEvalCreateSuiteCommand createSuiteCommand(
            String projectId,
            String agentId,
            Map<String, Object> request,
            String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        List<AgentEvalCase> cases = maps(safe.get("cases")).stream()
                .map(AgentEvalCase::fromMap)
                .toList();
        return new AgentEvalCreateSuiteCommand(
                text(safe.get("suiteId")),
                text(projectId),
                text(agentId),
                text(safe.get("name")),
                cases,
                text(actor));
    }

    public AgentEvalDefinitionSnapshot definition(OpsAgentDefinition source) {
        if (source == null) throw new IllegalArgumentException("AGENT_EVAL_DEFINITION_REQUIRED");
        List<AgentEvalNode> nodes = source.getNodes() == null ? List.of() : source.getNodes().stream()
                .map(this::node)
                .toList();
        List<AgentEvalEdge> edges = source.getEdges() == null ? List.of() : source.getEdges().stream()
                .map(this::edge)
                .toList();
        List<String> roles = source.getAgentscopeAgents() == null ? List.of() : source.getAgentscopeAgents().stream()
                .map(OpsAgentScopeConfig::getRole)
                .map(this::text)
                .filter(value -> !value.isBlank())
                .toList();
        return new AgentEvalDefinitionSnapshot(
                source.getProjectId(),
                source.getAgentId(),
                source.getVersion() == null ? 0 : source.getVersion(),
                source.getDefinitionHash(),
                source.getLifecycle(),
                source.getStartNodeId(),
                nodes,
                edges,
                roles);
    }

    public Map<String, Object> suiteView(AgentEvalSuite suite) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suiteId", suite.suiteId());
        result.put("projectId", suite.projectId());
        result.put("agentId", suite.agentId());
        result.put("name", suite.name());
        result.put("status", "ACTIVE");
        result.put("suiteVersion", suite.version());
        return result;
    }

    public Map<String, Object> runView(AgentEvalRunResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("evalRunId", result.evalRunId());
        response.put("suiteId", result.suiteId());
        response.put("status", result.status());
        response.put("agentId", result.agentId());
        response.put("agentVersion", result.agentVersion());
        response.put("definitionHash", result.definitionHash());
        response.put("baselineVersion", result.baselineVersion());
        response.put("regressionStatus", result.regressionStatus());
        response.put("totalCases", result.totalCases());
        response.put("passedCases", result.passedCases());
        response.put("failedCases", result.failedCases());
        List<Map<String, Object>> caseResults = new ArrayList<>();
        for (int index = 0; index < result.caseExecutions().size(); index++) {
            AgentEvalCaseExecution execution = result.caseExecutions().get(index);
            Map<String, Object> view = new LinkedHashMap<>(execution.result().toMap());
            view.put("caseId", execution.caseId());
            if (index < result.baselineResults().size()) {
                view.put("baseline", result.baselineResults().get(index).toMap());
            }
            caseResults.add(view);
        }
        response.put("caseResults", caseResults);
        return response;
    }

    private AgentEvalNode node(OpsWorkflowNode source) {
        return new AgentEvalNode(
                source == null ? "" : source.getNodeId(),
                source == null ? "" : source.getType(),
                source == null || source.getConfig() == null ? Map.of() : source.getConfig());
    }

    private AgentEvalEdge edge(OpsGraphEdge source) {
        return new AgentEvalEdge(
                source == null ? "" : source.getFrom(),
                source == null ? "" : source.getTo());
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, nested) -> normalized.put(String.valueOf(key), nested));
            result.add(normalized);
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
