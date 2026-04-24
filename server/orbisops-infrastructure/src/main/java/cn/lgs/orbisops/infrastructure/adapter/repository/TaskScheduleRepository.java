package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.schedule.ScheduledTaskRegistration;
import cn.lgs.orbisops.application.schedule.ScheduledTaskRegistryPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleCatalogPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.infrastructure.dao.IAiAgentTaskScheduleDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiAgentTaskSchedule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis adapter for typed scheduled-Agent definitions. */
@Repository
public class TaskScheduleRepository implements TaskScheduleCatalogPort, ScheduledTaskRegistryPort {

    private final ObjectProvider<IAiAgentTaskScheduleDao> daoProvider;
    private final TaskSchedulePersistenceCodec codec;

    public TaskScheduleRepository(
            ObjectProvider<IAiAgentTaskScheduleDao> daoProvider,
            ObjectMapper objectMapper) {
        this.daoProvider = daoProvider;
        this.codec = new TaskSchedulePersistenceCodec(objectMapper);
    }

    @Override
    public boolean insert(TaskScheduleDefinition schedule) {
        IAiAgentTaskScheduleDao dao = dao();
        return dao != null && dao.insert(toPo(schedule)) > 0;
    }

    @Override
    public boolean update(TaskScheduleDefinition schedule) {
        IAiAgentTaskScheduleDao dao = dao();
        return dao != null && dao.updateById(toPo(schedule)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        IAiAgentTaskScheduleDao dao = dao();
        return dao != null && dao.deleteById(id) > 0;
    }

    @Override
    public TaskScheduleDefinition findById(Long id) {
        IAiAgentTaskScheduleDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryById(id));
    }

    @Override
    public List<TaskScheduleDefinition> findByProjectId(String projectId) {
        IAiAgentTaskScheduleDao dao = dao();
        if (dao == null) {
            return List.of();
        }
        List<AiAgentTaskSchedule> rows = dao.queryByProjectId(projectId);
        return rows == null || rows.isEmpty()
                ? List.of()
                : rows.stream().map(this::toDefinition).toList();
    }

    @Override
    public List<ScheduledTaskRegistration> listEnabled() {
        IAiAgentTaskScheduleDao dao = dao();
        if (dao == null) {
            return List.of();
        }
        List<AiAgentTaskSchedule> rows = dao.queryAllValidTaskSchedule();
        return rows == null || rows.isEmpty()
                ? List.of()
                : rows.stream()
                .map(row -> new ScheduledTaskRegistration(
                        row.getId(),
                        row.getDescription(),
                        row.getCronExpression()))
                .toList();
    }

    @Override
    public List<Long> listInvalidIds() {
        IAiAgentTaskScheduleDao dao = dao();
        if (dao == null) {
            return List.of();
        }
        List<Long> ids = dao.queryAllInvalidTaskScheduleIds();
        return ids == null || ids.isEmpty() ? List.of() : List.copyOf(ids);
    }

    private IAiAgentTaskScheduleDao dao() {
        return daoProvider == null ? null : daoProvider.getIfAvailable();
    }

    private AiAgentTaskSchedule toPo(TaskScheduleDefinition schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_DEFINITION_REQUIRED");
        }
        return AiAgentTaskSchedule.builder()
                .id(schedule.id())
                .projectId(schedule.projectId())
                .createdBy(schedule.createdBy())
                .agentId(schedule.agentId())
                .taskName(schedule.taskName())
                .description(schedule.description())
                .cronExpression(schedule.cronExpression())
                .taskParam(codec.encode(schedule.runtimeConfiguration()))
                .status(schedule.status())
                .createTime(schedule.createTime())
                .updateTime(schedule.updateTime())
                .build();
    }

    private TaskScheduleDefinition toDefinition(AiAgentTaskSchedule row) {
        if (row == null) {
            return null;
        }
        TaskScheduleRuntimeConfiguration runtime = codec.decode(row.getTaskParam());
        String projectId = hasText(row.getProjectId())
                ? row.getProjectId().trim()
                : runtime.projectId();
        return new TaskScheduleDefinition(
                row.getId(),
                projectId,
                row.getAgentId(),
                row.getTaskName(),
                row.getDescription(),
                row.getCronExpression(),
                runtime,
                row.getStatus(),
                row.getCreateTime(),
                row.getUpdateTime(), row.getCreatedBy());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
