package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskScheduleAuditPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Operations audit adapter for scheduled Agent task configuration changes. */
public final class OpsTaskScheduleAuditAdapter implements TaskScheduleAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsTaskScheduleAuditAdapter(OpsConfigAuditService auditService) {
        if (auditService == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_AUDIT_SERVICE_REQUIRED");
        }
        this.auditService = auditService;
    }

    @Override
    public void created(TaskScheduleDefinition schedule) {
        auditService.record("task-schedule", "create", schedule.taskName(), null, schedule);
    }

    @Override
    public void updated(Long id, TaskScheduleDefinition before, TaskScheduleDefinition after) {
        auditService.record("task-schedule", "update", String.valueOf(id), before, after);
    }

    @Override
    public void statusChanged(Long id, TaskScheduleDefinition before, TaskScheduleDefinition after) {
        auditService.record("task-schedule", "status", String.valueOf(id), before, after);
    }

    @Override
    public void deleted(Long id, TaskScheduleDefinition before) {
        auditService.record("task-schedule", "delete", String.valueOf(id), before, Map.of("deleted", true));
    }
}
