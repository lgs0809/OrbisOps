package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.entity.TaskScheduleRecord;

import java.util.List;

public interface ITaskScheduleRepository {

    int insert(TaskScheduleRecord record);

    int updateById(TaskScheduleRecord record);

    int deleteById(Long id);

    TaskScheduleRecord queryById(Long id);

    List<TaskScheduleRecord> queryAll();

    List<TaskScheduleRecord> queryByProjectId(String projectId);
}
