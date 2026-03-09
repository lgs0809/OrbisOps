package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agent.adapter.repository.ITaskScheduleRepository;
import cn.lgs.orbisops.domain.agent.model.entity.TaskScheduleRecord;
import cn.lgs.orbisops.infrastructure.dao.IAiAgentTaskScheduleDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiAgentTaskSchedule;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Repository
public class TaskScheduleRepository implements ITaskScheduleRepository {

    @Autowired(required = false)
    private IAiAgentTaskScheduleDao dao;

    @Override
    public int insert(TaskScheduleRecord record) {
        return dao == null ? 0 : dao.insert(toPo(record));
    }

    @Override
    public int updateById(TaskScheduleRecord record) {
        return dao == null ? 0 : dao.updateById(toPo(record));
    }

    @Override
    public int deleteById(Long id) {
        return dao == null ? 0 : dao.deleteById(id);
    }

    @Override
    public TaskScheduleRecord queryById(Long id) {
        return dao == null ? null : toRecord(dao.queryById(id));
    }

    @Override
    public List<TaskScheduleRecord> queryAll() {
        return dao == null ? List.of() : toRecords(dao.queryAll());
    }

    @Override
    public List<TaskScheduleRecord> queryByProjectId(String projectId) {
        return dao == null ? List.of() : toRecords(dao.queryByProjectId(projectId));
    }

    private AiAgentTaskSchedule toPo(TaskScheduleRecord record) {
        AiAgentTaskSchedule po = new AiAgentTaskSchedule();
        BeanUtils.copyProperties(record, po);
        return po;
    }

    private TaskScheduleRecord toRecord(AiAgentTaskSchedule po) {
        if (po == null) {
            return null;
        }
        TaskScheduleRecord record = new TaskScheduleRecord();
        BeanUtils.copyProperties(po, record);
        return record;
    }

    private List<TaskScheduleRecord> toRecords(List<AiAgentTaskSchedule> rows) {
        return rows == null ? List.of() : rows.stream().map(this::toRecord).toList();
    }
}
