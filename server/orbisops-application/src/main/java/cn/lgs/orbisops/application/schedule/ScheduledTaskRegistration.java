package cn.lgs.orbisops.application.schedule;

/** Minimal read model required by the dynamic scheduler registry. */
public record ScheduledTaskRegistration(
        Long id,
        String description,
        String cronExpression) {
}
