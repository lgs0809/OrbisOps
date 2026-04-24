package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.incident.IncidentCommandApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthApplicationService;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCatalogPort;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionUseCase;
import cn.lgs.orbisops.trigger.application.config.OpsTaskScheduleRuntimeConfigurationCodec;
import cn.lgs.orbisops.trigger.application.ops.OpsAnalysisApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsScheduledPrometheusScreeningService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;

/** Spring assembly for scheduled task execution lifecycle. */
@Configuration
public class ScheduledTaskExecutionConfiguration {

    @Bean
    public ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO>
    scheduledTaskExecutionUseCase(
            ScheduledTaskExecutionCatalogPort catalogPort,
            @Qualifier("opsRunExecutor") ThreadPoolExecutor executor,
            OpsAnalysisApplicationService analysisService,
            OpsTaskScheduleRuntimeConfigurationCodec runtimeCodec,
            ProjectDefinitionApplicationService projects,
            ResourceHealthApplicationService resourceHealth,
            OpsScheduledPrometheusScreeningService prometheusScreening,
            IncidentCommandApplicationService incidents) {
        return new ScheduledTaskExecutionUseCase<>(
                catalogPort,
                new OpsScheduledTaskExecutionExecutorAdapter(executor),
                new OpsScheduledTaskAnalysisAdapter(analysisService, runtimeCodec, projects),
                new OpsScheduledTaskResourceHealthScreeningAdapter(resourceHealth, runtimeCodec, prometheusScreening),
                new OpsScheduledTaskIncidentAdapter(incidents, runtimeCodec));
    }
}
