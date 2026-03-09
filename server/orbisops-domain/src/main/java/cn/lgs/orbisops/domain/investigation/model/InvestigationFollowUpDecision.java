package cn.lgs.orbisops.domain.investigation.model;

import java.util.List;

/** Deterministic follow-up tasks and user-visible execution notes. */
public record InvestigationFollowUpDecision(List<InvestigationFollowUpTask> tasks,
                                            List<String> notes) {

    public InvestigationFollowUpDecision {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
