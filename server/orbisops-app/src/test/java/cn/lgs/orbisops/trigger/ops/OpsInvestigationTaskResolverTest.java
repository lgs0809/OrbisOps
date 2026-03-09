package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpsInvestigationTaskResolverTest {

    private final OpsInvestigationTaskResolver resolver =
            new OpsInvestigationTaskResolver();

    @Test
    void prefersPrimaryPlanTaskOverConditionalTask() {
        OpsAnalysisResponseDTO.InvestigationTaskDTO primary = task(
                "elasticsearch",
                "primary-agent",
                1);
        OpsAnalysisResponseDTO.InvestigationTaskDTO conditional = task(
                "elasticsearch",
                "conditional-agent",
                2);
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .tasks(List.of(primary))
                        .conditionalTasks(List.of(conditional))
                        .build();

        assertSame(primary, resolver.resolve(plan, "elasticsearch"));
    }

    @Test
    void resolvesConditionalTaskWhenPrimaryTaskIsMissing() {
        OpsAnalysisResponseDTO.InvestigationTaskDTO conditional = task(
                "prometheus",
                "conditional-agent",
                null);
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .tasks(List.of())
                        .conditionalTasks(List.of(conditional))
                        .build();

        assertSame(conditional, resolver.resolve(plan, "prometheus"));
    }

    @Test
    void materializesLegacyFallbackTaskForUnknownSource() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .tasks(List.of())
                        .conditionalTasks(List.of())
                        .build();

        OpsAnalysisResponseDTO.InvestigationTaskDTO task =
                resolver.resolve(plan, "custom");

        assertEquals("custom", task.getSource());
        assertEquals("custom-agent", task.getAgent());
        assertEquals("查询 custom 数据源。", task.getGoal());
        assertEquals("主 Agent follow-up 需要补充该数据源证据。", task.getReason());
        assertEquals(3, task.getPriority());
        assertEquals("main reflection", task.getCondition());
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(
            String source,
            String agent,
            Integer priority) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(agent)
                .priority(priority)
                .build();
    }
}
