package cn.lgs.orbisops.domain.investigation.model;

/** Follow-up task proposed by deterministic investigation policy. */
public record InvestigationFollowUpTask(String source,
                                        String agent,
                                        String goal,
                                        String reason,
                                        Integer priority,
                                        String condition) {

    public InvestigationFollowUpTask {
        source = text(source);
        agent = text(agent);
        goal = text(goal);
        reason = text(reason);
        condition = text(condition);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
