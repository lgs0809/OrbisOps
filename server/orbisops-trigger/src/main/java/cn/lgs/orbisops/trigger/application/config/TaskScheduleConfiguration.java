package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskExecutionCatalogPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleAgentSnapshotPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleAuditPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleCatalogPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleCatalogUseCase;
import cn.lgs.orbisops.application.schedule.TaskScheduleCronValidationPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleExecutionPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleExecutionUseCase;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.job.AgentTaskExecutionService;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring assembly for scheduled Agent task Application boundaries. */
@Configuration
public class TaskScheduleConfiguration {

    @Bean
    public OpsTaskScheduleRuntimeConfigurationCodec opsTaskScheduleRuntimeConfigurationCodec() {
        return new OpsTaskScheduleRuntimeConfigurationCodec();
    }

    @Bean
    public TaskScheduleAuditPort taskScheduleAuditPort(OpsConfigAuditService auditService) {
        return new OpsTaskScheduleAuditAdapter(auditService);
    }

    @Bean
    public TaskScheduleAgentSnapshotPort taskScheduleAgentSnapshotPort(
            OpsAgentDefinitionQueryGateway queryGateway) {
        return new OpsTaskScheduleAgentSnapshotAdapter(queryGateway);
    }

    @Bean
    public TaskScheduleCronValidationPort taskScheduleCronValidationPort() {
        return new OpsTaskScheduleCronValidationAdapter();
    }

    @Bean
    public TaskScheduleExecutionPort taskScheduleExecutionPort(
            AgentTaskExecutionService executionService,
            OpsTaskScheduleRuntimeConfigurationCodec codec) {
        return new OpsTaskScheduleExecutionAdapter(executionService, codec);
    }

    @Bean
    public TaskScheduleCatalogUseCase taskScheduleCatalogUseCase(
            TaskScheduleCatalogPort catalogPort,
            TaskScheduleAuditPort auditPort,
            TaskScheduleAgentSnapshotPort agentSnapshotPort,
            TaskScheduleCronValidationPort cronValidationPort) {
        return new TaskScheduleCatalogUseCase(
                catalogPort,
                auditPort,
                agentSnapshotPort,
                cronValidationPort);
    }

    @Bean
    public TaskScheduleExecutionUseCase taskScheduleExecutionUseCase(
            TaskScheduleCatalogPort catalogPort,
            TaskScheduleExecutionPort executionPort,
            TaskExecutionCatalogPort executionCatalogPort) {
        return new TaskScheduleExecutionUseCase(catalogPort, executionPort, executionCatalogPort);
    }
}
