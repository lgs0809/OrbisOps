package cn.lgs.orbisops.application.schedule;

/** Published Agent Definition snapshot bound to a scheduled task. */
public record TaskScheduleAgentSnapshot(
        Integer version,
        String definitionHash) {
}
