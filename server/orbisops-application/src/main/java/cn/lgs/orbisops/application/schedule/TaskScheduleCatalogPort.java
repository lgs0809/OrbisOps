package cn.lgs.orbisops.application.schedule;

import java.util.List;

/** Persistence boundary for scheduled Agent task definitions. */
public interface TaskScheduleCatalogPort {

    boolean insert(TaskScheduleDefinition schedule);

    boolean update(TaskScheduleDefinition schedule);

    boolean deleteById(Long id);

    TaskScheduleDefinition findById(Long id);

    List<TaskScheduleDefinition> findByProjectId(String projectId);
}
