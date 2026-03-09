package cn.lgs.orbisops.application.schedule;

/** Audit boundary for scheduled Agent task configuration changes. */
public interface TaskScheduleAuditPort {

    void created(TaskScheduleDefinition schedule);

    void updated(Long id, TaskScheduleDefinition before, TaskScheduleDefinition after);

    void statusChanged(Long id, TaskScheduleDefinition before, TaskScheduleDefinition after);

    void deleted(Long id, TaskScheduleDefinition before);
}
