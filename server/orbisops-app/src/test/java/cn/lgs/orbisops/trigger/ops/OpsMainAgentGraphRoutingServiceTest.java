package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainAgentGraphRoutingServiceTest {

    private final OpsMainAgentGraphRoutingService service =
            new OpsMainAgentGraphRoutingService(new OpsMainAgentDeterministicPlanningService());

    @Test
    void parsesSupportedChoiceEncodingsAndIgnoresDefaultRoutes() {
        String choices = """
                {
                  "routers":[{
                    "choices":[
                      {"routeKey":"metrics","conditionType":"route_match"},
                      {"condition":"needs:elasticsearch","conditionType":"expression"},
                      {"routeOutputHint":"tasks[].routeKey=mysql_slow_sql remaining","conditionType":"expression"},
                      {"condition":"replan_required","conditionType":"expression"},
                      {"routeKey":"rag","conditionType":"default"},
                      {"routeKey":"ignored","conditionType":"always"}
                    ]
                  }]
                }
                """;

        Set<String> sources = service.routeSources(choices);

        assertEquals(Set.of(
                OpsMainAgentPlanner.SOURCE_PROM,
                OpsMainAgentPlanner.SOURCE_ES,
                OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL,
                "replan_required"), sources);
    }

    @Test
    void filtersAllPlanTaskBucketsAndPreservesPlanMetadata() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("INCIDENT_INVESTIGATION")
                        .reason("mixed")
                        .changeRequested(true)
                        .changeIntent("生成修复提案")
                        .tasks(List.of(task("metrics"), task(OpsMainAgentPlanner.SOURCE_ES)))
                        .conditionalTasks(List.of(task(OpsMainAgentPlanner.SOURCE_RAG)))
                        .skippedTasks(List.of(task(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL)))
                        .build();
        String choices = """
                {"routers":[{"choices":[
                  {"routeKey":"prometheus","conditionType":"route_match"},
                  {"routeKey":"rag","conditionType":"route_match"}
                ]}]}
                """;

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO filtered =
                service.filterPlan(plan, choices);

        assertEquals("INCIDENT_INVESTIGATION", filtered.getIntent());
        assertEquals("mixed", filtered.getReason());
        assertEquals(Boolean.TRUE, filtered.getChangeRequested());
        assertEquals("生成修复提案", filtered.getChangeIntent());
        assertEquals(List.of("metrics"), sources(filtered.getTasks()));
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(filtered.getConditionalTasks()));
        assertTrue(filtered.getSkippedTasks().isEmpty());
    }

    @Test
    void missingRoutersOrInvalidJsonKeepsLegacyNoFilterBehavior() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .intent("METRIC_FIRST_INVESTIGATION")
                        .reason("reason")
                        .tasks(List.of(task(OpsMainAgentPlanner.SOURCE_PROM)))
                        .conditionalTasks(List.of())
                        .skippedTasks(List.of())
                        .build();

        assertTrue(service.routeSources("{\"choices\":[]}").isEmpty());
        assertTrue(service.routeSources("not-json").isEmpty());
        assertSame(plan, service.filterPlan(plan, "{\"choices\":[]}"));
        assertSame(plan, service.filterPlan(plan, "not-json"));
    }

    @Test
    void emptyPlanKeepsLegacyMutableBucketsAndChangeDefaults() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                service.emptyPlan("no route");

        assertEquals("STOP", plan.getIntent());
        assertEquals("no route", plan.getReason());
        assertEquals(Boolean.FALSE, plan.getChangeRequested());
        assertEquals("", plan.getChangeIntent());
        plan.getTasks().add(task(OpsMainAgentPlanner.SOURCE_RAG));
        assertFalse(plan.getTasks().isEmpty());
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(String source) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(source + "-agent")
                .goal("goal")
                .reason("reason")
                .priority(1)
                .build();
    }

    private List<String> sources(List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        return tasks.stream().map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource).toList();
    }
}
