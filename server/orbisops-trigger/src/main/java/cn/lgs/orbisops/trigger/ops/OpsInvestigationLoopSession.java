package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.Deque;
import java.util.List;
import java.util.Set;

/** Mutable application state for one unified Investigation loop invocation. */
final class OpsInvestigationLoopSession {

    final List<OpsAnalysisResponseDTO.InvestigationResultDTO> results;
    final List<String> executionNotes;
    final Set<String> availableSources;
    final Set<String> executedSources;
    final Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue;
    final int maxAdjustments;
    final int maxExecutions;
    int adjustments;
    int guard;

    OpsInvestigationLoopSession(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            List<String> executionNotes,
            Set<String> availableSources,
            Set<String> executedSources,
            Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue,
            int maxAdjustments,
            int maxExecutions) {
        this.results = results;
        this.executionNotes = executionNotes;
        this.availableSources = availableSources;
        this.executedSources = executedSources;
        this.queue = queue;
        this.maxAdjustments = maxAdjustments;
        this.maxExecutions = maxExecutions;
    }
}
