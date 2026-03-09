package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Applies thread-safe runtime note projections to the analysis response DTO. */
final class OpsAnalysisResponseNotes {

    void append(OpsAnalysisResponseDTO response, List<String> notes) {
        if (response == null || notes == null || notes.isEmpty()) {
            return;
        }
        synchronized (response) {
            List<String> merged = new ArrayList<>(
                    Optional.ofNullable(response.getExecutionNotes()).orElse(List.of()));
            merged.addAll(notes);
            response.setExecutionNotes(merged);
        }
    }

    void mergeRuntimeNotes(
            OpsAnalysisResponseDTO response,
            List<String> runtimeNotes) {
        if (response == null) {
            return;
        }
        List<String> current = Optional.ofNullable(response.getExecutionNotes())
                .orElse(List.of());
        List<String> merged = new ArrayList<>();
        for (String note : Optional.ofNullable(runtimeNotes).orElse(List.of())) {
            if (!current.contains(note) && !merged.contains(note)) {
                merged.add(note);
            }
        }
        merged.addAll(current);
        response.setExecutionNotes(merged);
    }
}
