package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Application-level priority scheduling for Investigation tasks. */
final class OpsInvestigationTaskQueue {

    Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> create(
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        return Optional.ofNullable(tasks).orElse(List.of()).stream()
                .sorted(Comparator.comparing(this::priority))
                .collect(Collectors.toCollection(ArrayDeque::new));
    }

    List<OpsAnalysisResponseDTO.InvestigationTaskDTO> pollNextPriorityBatch(
            Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue,
            Set<String> executedSources,
            boolean parallelExecutionEnabled) {
        if (queue == null || queue.isEmpty()) return List.of();
        Set<String> executed = executedSources == null ? Set.of() : executedSources;
        Optional<Integer> minPriority = queue.stream()
                .filter(task -> task != null && !executed.contains(task.getSource()))
                .map(this::priority)
                .min(Integer::compareTo);
        if (minPriority.isEmpty()) {
            queue.clear();
            return List.of();
        }

        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> batch = new ArrayList<>();
        Set<String> batchSources = new HashSet<>();
        Iterator<OpsAnalysisResponseDTO.InvestigationTaskDTO> iterator = queue.iterator();
        while (iterator.hasNext()) {
            OpsAnalysisResponseDTO.InvestigationTaskDTO task = iterator.next();
            if (task == null || executed.contains(task.getSource())) {
                iterator.remove();
                continue;
            }
            if (priority(task) == minPriority.get()
                    && batchSources.add(task.getSource())) {
                batch.add(task);
                iterator.remove();
            }
        }
        if (!parallelExecutionEnabled && batch.size() > 1) {
            OpsAnalysisResponseDTO.InvestigationTaskDTO first = batch.remove(0);
            queue.addAll(batch);
            return List.of(first);
        }
        return List.copyOf(batch);
    }

    Set<String> queuedSources(
            Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue) {
        if (queue == null || queue.isEmpty()) return Set.of();
        Set<String> sources = new java.util.LinkedHashSet<>();
        queue.stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .filter(this::hasText)
                .forEach(sources::add);
        return java.util.Collections.unmodifiableSet(sources);
    }

    String remainingSources(
            Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue) {
        if (queue == null || queue.isEmpty()) return "";
        return queue.stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .collect(Collectors.joining(","));
    }

    private int priority(OpsAnalysisResponseDTO.InvestigationTaskDTO task) {
        return Optional.ofNullable(task.getPriority()).orElse(99);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
