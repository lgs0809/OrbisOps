package cn.lgs.orbisops.application.schedule;

/** Submission boundary for executing one scheduled Agent task. */
public interface TaskScheduleExecutionPort {

    Long submit(TaskScheduleDefinition schedule, String triggerType);
}
