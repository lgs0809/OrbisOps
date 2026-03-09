package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionCompletionReconciliationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionCatalogPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionCheckpointPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionDispatchPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionEmergencyStopPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionRecordPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionTransactionPort;
import cn.lgs.orbisops.application.toolexecution.ToolInvocationCoordinator;
import cn.lgs.orbisops.application.toolexecution.ToolInvocationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class OpsToolExecutionApplicationConfiguration {

    @Bean
    public ToolExecutionApplicationService toolExecutionApplicationService(
            ToolExecutionCatalogPort catalog,
            ToolExecutionDispatchPort dispatch,
            ToolExecutionRecordPort records,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit,
            ToolExecutionIdempotencyPort idempotency,
            ToolExecutionTransactionPort transactions,
            ToolExecutionEmergencyStopPort emergencyStop) {
        return new ToolExecutionApplicationService(
                catalog,
                dispatch,
                records,
                checkpoints,
                audit,
                idempotency,
                transactions,
                emergencyStop,
                () -> "tool-call-" + UUID.randomUUID(),
                System::nanoTime,
                Clock.systemUTC());
    }

    @Bean
    public ToolInvocationPort toolInvocationPort(
            ToolExecutionApplicationService execution) {
        return new ToolInvocationCoordinator(execution);
    }

    @Bean
    public ToolExecutionCompletionReconciliationService toolExecutionCompletionReconciliationService(
            ToolExecutionIdempotencyPort idempotency,
            ToolExecutionCheckpointPort checkpoints,
            ToolExecutionAuditPort audit) {
        return new ToolExecutionCompletionReconciliationService(
                idempotency, checkpoints, audit, Clock.systemUTC());
    }
}
