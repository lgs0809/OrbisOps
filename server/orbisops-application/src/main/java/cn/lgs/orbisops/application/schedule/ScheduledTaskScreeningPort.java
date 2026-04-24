package cn.lgs.orbisops.application.schedule;

/** Cheap read-only screening boundary for a scheduled inspection slot. */
public interface ScheduledTaskScreeningPort {

    ScheduledTaskScreeningResult screen(ScheduledTaskExecutionCommand command);
}
