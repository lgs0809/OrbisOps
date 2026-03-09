package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsInvestigationTaskQueueTest {

    private final OpsInvestigationTaskQueue queuePolicy =
            new OpsInvestigationTaskQueue();

    @Test
    void createSortsByPriorityAndTreatsNullAsNinetyNine() {
        Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue = queuePolicy.create(List.of(
                task("rag", 3),
                task("unknown", null),
                task("elasticsearch", 1),
                task("prometheus", 2)));

        assertEquals(
                List.of("elasticsearch", "prometheus", "rag", "unknown"),
                queue.stream()
                        .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                        .toList());
    }

    @Test
    void createPreservesLegacyNullTaskFailure() {
        java.util.ArrayList<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks =
                new java.util.ArrayList<>();
        tasks.add(null);

        assertThrows(NullPointerException.class, () -> queuePolicy.create(tasks));
    }

    @Test
    void parallelPollReturnsUniqueSourcesAtMinimumPriority() {
        Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue = new ArrayDeque<>(List.of(
                task("elasticsearch", 1),
                task("elasticsearch", 1),
                task("prometheus", 1),
                task("rag", 2)));

        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> batch =
                queuePolicy.pollNextPriorityBatch(queue, Set.of(), true);

        assertEquals(
                List.of("elasticsearch", "prometheus"),
                batch.stream()
                        .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                        .toList());
        assertEquals(
                List.of("elasticsearch", "rag"),
                queue.stream()
                        .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                        .toList());
    }

    @Test
    void serialPollReturnsFirstAndRequeuesSamePriorityPeers() {
        Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue = new ArrayDeque<>(List.of(
                task("elasticsearch", 1),
                task("prometheus", 1),
                task("rag", 2)));

        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> first =
                queuePolicy.pollNextPriorityBatch(queue, Set.of(), false);
        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> second =
                queuePolicy.pollNextPriorityBatch(queue, Set.of("elasticsearch"), false);

        assertEquals(List.of("elasticsearch"), first.stream()
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .toList());
        assertEquals(List.of("prometheus"), second.stream()
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .toList());
    }

    @Test
    void executedSourcesAreRemovedAndExhaustedQueueIsCleared() {
        Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue = new ArrayDeque<>(List.of(
                task("elasticsearch", 1),
                task("prometheus", 2)));

        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> batch =
                queuePolicy.pollNextPriorityBatch(
                        queue,
                        Set.of("elasticsearch", "prometheus"),
                        true);

        assertTrue(batch.isEmpty());
        assertTrue(queue.isEmpty());
    }

    @Test
    void queuedAndRemainingSourceViewsAreStable() {
        Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue = new ArrayDeque<>(List.of(
                task("prometheus", 1),
                task("prometheus", 2),
                task("rag", 3)));

        assertEquals(Set.of("prometheus", "rag"), queuePolicy.queuedSources(queue));
        assertEquals("prometheus,prometheus,rag", queuePolicy.remainingSources(queue));
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(
            String source,
            Integer priority) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(source + "-agent")
                .priority(priority)
                .build();
    }
}
