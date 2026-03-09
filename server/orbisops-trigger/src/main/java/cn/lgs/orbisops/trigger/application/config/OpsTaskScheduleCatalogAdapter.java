package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskScheduleCatalogPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.domain.agent.adapter.repository.ITaskScheduleRepository;
import cn.lgs.orbisops.domain.agent.model.entity.TaskScheduleRecord;

import java.util.List;

/** Repository and JSON persistence adapter for scheduled Agent task definitions. */
public final class OpsTaskScheduleCatalogAdapter implements TaskScheduleCatalogPort {

    private final ITaskScheduleRepository repository;
    private final OpsTaskScheduleRuntimeConfigurationCodec codec;

    public OpsTaskScheduleCatalogAdapter(
            ITaskScheduleRepository repository,
            OpsTaskScheduleRuntimeConfigurationCodec codec) {
        if (repository == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_REPOSITORY_REQUIRED");
        }
        if (codec == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_RUNTIME_CODEC_REQUIRED");
        }
        this.repository = repository;
        this.codec = codec;
    }

    @Override
    public boolean insert(TaskScheduleDefinition schedule) {
        return repository.insert(record(schedule)) > 0;
    }

    @Override
    public boolean update(TaskScheduleDefinition schedule) {
        return repository.updateById(record(schedule)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id) > 0;
    }

    @Override
    public TaskScheduleDefinition findById(Long id) {
        return definition(repository.queryById(id));
    }

    @Override
    public List<TaskScheduleDefinition> findByProjectId(String projectId) {
        List<TaskScheduleRecord> values = repository.queryByProjectId(projectId);
        return values == null || values.isEmpty()
                ? List.of()
                : values.stream().map(this::definition).toList();
    }

    TaskScheduleRecord record(TaskScheduleDefinition schedule) {
        if (schedule == null) {
            return null;
        }
        return TaskScheduleRecord.builder()
                .id(schedule.id())
                .projectId(schedule.projectId())
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

    private TaskScheduleDefinition definition(TaskScheduleRecord record) {
        if (record == null) {
            return null;
        }
        TaskScheduleRuntimeConfiguration runtime = codec.decode(record.getTaskParam());
        String projectId = hasText(record.getProjectId())
                ? record.getProjectId().trim()
                : runtime.projectId();
        return new TaskScheduleDefinition(
                record.getId(),
                projectId,
                record.getAgentId(),
                record.getTaskName(),
                record.getDescription(),
                record.getCronExpression(),
                runtime,
                record.getStatus(),
                record.getCreateTime(),
                record.getUpdateTime());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
