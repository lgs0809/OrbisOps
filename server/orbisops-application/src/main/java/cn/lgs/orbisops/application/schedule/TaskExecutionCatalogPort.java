package cn.lgs.orbisops.application.schedule;

import java.util.List;

/** Query boundary for scheduled task execution history. */
public interface TaskExecutionCatalogPort {

    List<TaskExecutionView> list(Long scheduleId, int limit);
}
