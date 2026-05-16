package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisRunProcessManager;
import cn.lgs.orbisops.application.audit.AnalysisAuditApplicationService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisRunPersistenceMapper;
import cn.lgs.orbisops.trigger.application.analysis.OpsAsyncAnalysisRunProtocolMapper;
import cn.lgs.orbisops.trigger.application.audit.OpsAnalysisAuditMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

/** Spring assembly for the asynchronous analysis run process manager. */
@Configuration
public class OpsAsyncAnalysisRunConfiguration {

    @Bean
    public OpsAsyncAnalysisRunProtocolMapper opsAsyncAnalysisRunProtocolMapper() {
        return new OpsAsyncAnalysisRunProtocolMapper();
    }

    @Bean
    public AsyncAnalysisRunProcessManager<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO>
    asyncAnalysisRunProcessManager(
            @Qualifier("opsRunExecutor") ThreadPoolExecutor executor,
            AnalysisAuditApplicationService audits,
            OpsAnalysisAuditMapper auditMapper,
            OpsTelemetryService telemetry,
            GraphEventApplicationService graphEvents,
            AlertEventApplicationService alertEvents,
            OpsRunCancellationRegistry cancellationRegistry,
            IAnalysisRunRepository repository,
            OpsAnalysisRunPersistenceMapper persistenceMapper,
            OpsAnalysisRunSettings settings,
            OpsAsyncAnalysisRunProtocolMapper protocolMapper) {
        OpsAnalysisRunStore store = new OpsAnalysisRunStore(repository, persistenceMapper, settings);
        return new AsyncAnalysisRunProcessManager<>(
                new OpsAsyncAnalysisRunStoreAdapter(store, protocolMapper),
                new OpsAsyncAnalysisExecutionAdapter(executor, settings),
                new OpsAsyncAnalysisCancellationAdapter(cancellationRegistry),
                new OpsAsyncAnalysisRequestAdapter(),
                new OpsAsyncAnalysisTelemetryAdapter(telemetry),
                new OpsAsyncAnalysisAuditAdapter(audits, auditMapper),
                new OpsAsyncAnalysisOutcomeAdapter(
                        graphEvents,
                        new OpsAnalysisRunAlertOutcomeReporter(alertEvents)),
                () -> "ops_run_" + UUID.randomUUID().toString().replace("-", ""),
                Clock.systemDefaultZone());
    }
}
