package cn.lgs.orbisops.domain.investigation;

import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningObservation;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningPlan;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningTask;
import cn.lgs.orbisops.domain.investigation.service.InvestigationReplanPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationReplanPolicyTest {

    private final InvestigationReplanPolicy policy = new InvestigationReplanPolicy();

    @Test
    void explicitPreviousChangeIntentOverridesCandidateWithoutChangingTasks() {
        InvestigationPlanningPlan target = plan(
                false,
                "old",
                List.of(task("prometheus")),
                List.of(task("rag")));
        InvestigationPlanningPlan source = plan(
                true,
                "restart payment",
                List.of(),
                List.of());

        InvestigationPlanningPlan inherited = policy.inheritChangeIntent(target, source);

        assertTrue(inherited.changeRequested());
        assertEquals("restart payment", inherited.changeIntent());
        assertEquals(target.tasks(), inherited.tasks());
        assertEquals(target.conditionalTasks(), inherited.conditionalTasks());
    }

    @Test
    void missingPreviousChangeFactsPreserveCandidateValues() {
        InvestigationPlanningPlan target = plan(
                false,
                "keep-me",
                List.of(),
                List.of());
        InvestigationPlanningPlan source = plan(
                null,
                "",
                List.of(),
                List.of());

        InvestigationPlanningPlan inherited = policy.inheritChangeIntent(target, source);

        assertFalse(inherited.changeRequested());
        assertEquals("keep-me", inherited.changeIntent());
        assertNull(policy.inheritChangeIntent(null, source));
    }

    @Test
    void executedSourceAliasesAreRemovedFromBothRunnableCollections() {
        InvestigationPlanningPlan candidate = plan(
                null,
                "",
                java.util.Arrays.asList(task("prometheus"), task("elasticsearch"), null),
                java.util.Arrays.asList(task("PROM"), task("knowledge"), null));

        InvestigationPlanningPlan filtered = policy.excludeExecutedSources(
                candidate,
                List.of(new InvestigationPlanningObservation(
                        "metrics", "FOUND", "", List.of(), List.of(), List.of())));

        assertEquals(List.of("elasticsearch"),
                filtered.tasks().stream().map(InvestigationPlanningTask::source).toList());
        assertEquals(List.of("knowledge"),
                filtered.conditionalTasks().stream()
                        .map(InvestigationPlanningTask::source).toList());
    }

    private InvestigationPlanningPlan plan(
            Boolean changeRequested,
            String changeIntent,
            List<InvestigationPlanningTask> tasks,
            List<InvestigationPlanningTask> conditionalTasks) {
        return new InvestigationPlanningPlan(
                "REPLAN_CONTINUE",
                "reason",
                changeRequested,
                changeIntent,
                tasks,
                conditionalTasks,
                List.of(task("mysql_slow_sql")));
    }

    private InvestigationPlanningTask task(String source) {
        return new InvestigationPlanningTask(source, "agent", "goal", "reason", 1, null);
    }
}
