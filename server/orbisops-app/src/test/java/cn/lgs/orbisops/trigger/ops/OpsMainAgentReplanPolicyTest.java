package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainAgentReplanPolicyTest {

    private final OpsMainAgentReplanPolicy policy = new OpsMainAgentReplanPolicy();
    private final OpsMainAgentDeterministicPlanningService planning =
            new OpsMainAgentDeterministicPlanningService();

    @Test
    void preservesChangeIntentAndFiltersExecutedSourcesFromBothTaskCollections() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO previous =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .changeRequested(true)
                        .changeIntent("restart payment")
                        .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .tasks(new ArrayList<>(List.of(task("prometheus"), task("elasticsearch"))))
                        .conditionalTasks(new ArrayList<>(List.of(task("PROM"), task("rag"))))
                        .build();

        policy.inheritChangeIntent(plan, previous);
        policy.excludeExecutedSources(
                plan,
                List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("prometheus")
                        .build()),
                planning);

        assertTrue(plan.getChangeRequested());
        assertEquals("restart payment", plan.getChangeIntent());
        assertEquals(List.of("elasticsearch"), sources(plan.getTasks()));
        assertEquals(List.of("rag"), sources(plan.getConditionalTasks()));
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(String source) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .build();
    }

    private List<String> sources(List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        return tasks.stream().map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource).toList();
    }
}
