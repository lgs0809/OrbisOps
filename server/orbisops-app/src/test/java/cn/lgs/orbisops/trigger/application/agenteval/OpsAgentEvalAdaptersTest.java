package cn.lgs.orbisops.trigger.application.agenteval;

import cn.lgs.orbisops.application.agenteval.AgentEvalIdentityFactory;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseExecution;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCaseResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalDefinitionSnapshot;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsGraphEdge;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAgentEvalAdaptersTest {

    @Test
    void mapperConvertsDefinitionAndKeepsLegacySuiteAndRunViews() {
        OpsAgentEvalMapper mapper = new OpsAgentEvalMapper();
        OpsAgentDefinition source = OpsAgentDefinition.builder()
                .projectId("project-1")
                .agentId("agent-1")
                .version(3)
                .definitionHash("a".repeat(64))
                .lifecycle("VALIDATED")
                .startNodeId("start")
                .nodes(List.of(
                        OpsWorkflowNode.builder().nodeId("start").type("START").build(),
                        OpsWorkflowNode.builder().nodeId("end").type("END")
                                .config(Map.of("output", true)).build()))
                .edges(List.of(OpsGraphEdge.builder().from("start").to("end").build()))
                .agentscopeAgents(List.of(OpsAgentScopeConfig.builder().role("INVESTIGATOR").build()))
                .build();

        AgentEvalDefinitionSnapshot definition = mapper.definition(source);
        assertEquals("VALIDATED", definition.lifecycle());
        assertEquals(3, definition.version());
        assertEquals("a".repeat(64), definition.definitionHash());
        assertEquals(List.of("INVESTIGATOR"), definition.agentRoles());
        assertEquals(Map.of("output", true), definition.nodes().get(1).configuration());

        AgentEvalCase evalCase = AgentEvalCase.fromMap(map(
                "caseId", "case-1",
                "input", "帮我查错误日志",
                "expectedIntent", "OPS_INVESTIGATION"));
        AgentEvalSuite suite = new AgentEvalSuite(
                "suite-1", "project-1", "agent-1", "gate", 1, List.of(evalCase), "alice");
        Map<String, Object> suiteView = mapper.suiteView(suite);
        assertEquals(Set.of("suiteId", "projectId", "agentId", "name", "status", "suiteVersion"),
                suiteView.keySet());

        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        AgentEvalCaseResult candidate = new AgentEvalCaseResult(true, 1D, List.of(), Map.of("actualIntent", "OPS_INVESTIGATION"));
        AgentEvalCaseResult baseline = new AgentEvalCaseResult(true, 1D, List.of(), Map.of("actualIntent", "OPS_INVESTIGATION"));
        AgentEvalCaseExecution execution = new AgentEvalCaseExecution(
                "agent-eval-case-run-1", "agent-eval-run-1", "case-1",
                "project-1", "agent-1", 3, candidate, now, now.plusMillis(1));
        AgentEvalRunResult result = new AgentEvalRunResult(
                "agent-eval-run-1", "suite-1", "project-1", "agent-1", 3,
                "a".repeat(64), 2, "PASSED", "PASSED", 1, 1, 0,
                List.of(execution), List.of(baseline));
        Map<String, Object> runView = mapper.runView(result);

        assertEquals("agent-eval-run-1", runView.get("evalRunId"));
        assertEquals("PASSED", runView.get("regressionStatus"));
        List<?> caseResults = (List<?>) runView.get("caseResults");
        assertEquals(1, caseResults.size());
        assertTrue(((Map<?, ?>) caseResults.get(0)).containsKey("baseline"));
    }

    @Test
    void identityAdapterPreservesHistoricalPrefixes() {
        AgentEvalIdentityFactory adapter = new AgentEvalIdentityFactory(
                () -> "fixed",
                Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC));

        assertTrue(adapter.newSuiteId().startsWith("agent-eval-suite-"));
        assertTrue(adapter.newRunId().startsWith("agent-eval-run-"));
        assertTrue(adapter.newCaseRunId().startsWith("agent-eval-case-run-"));
    }

    @Test
    void auditAdapterUsesLegacyActionsAndRetainsActorInPayload() {
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentEvalAuditAdapter adapter = new OpsAgentEvalAuditAdapter(audit);
        AgentEvalCase evalCase = AgentEvalCase.fromMap(map(
                "caseId", "case-1",
                "input", "帮我查错误日志",
                "expectedIntent", "OPS_INVESTIGATION"));
        AgentEvalSuite suite = new AgentEvalSuite(
                "suite-1", "project-1", "agent-1", "gate", 1, List.of(evalCase), "alice");

        adapter.recordSuiteCreated(suite);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> suitePayload = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq("agent-eval"), eq("suite-create"), eq("suite-1"), isNull(), suitePayload.capture());
        assertEquals("alice", suitePayload.getValue().get("actor"));
        assertEquals(1, suitePayload.getValue().get("caseCount"));

        AgentEvalRunResult result = new AgentEvalRunResult(
                "agent-eval-run-1", "suite-1", "project-1", "agent-1", 3,
                "a".repeat(64), 2, "PASSED", "FAILED", 1, 0, 1,
                List.of(), List.of());
        adapter.recordRunCompleted(result, "bob");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> runPayload = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq("agent-eval"), eq("run-failed"), eq("agent-eval-run-1"),
                isNull(), runPayload.capture());
        assertEquals("bob", runPayload.getValue().get("actor"));
        assertEquals("a".repeat(64), runPayload.getValue().get("definitionHash"));
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
