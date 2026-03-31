package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionCompletionReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class OpsToolExecutionCompletionReconciliationJob {

    private final ToolExecutionCompletionReconciliationService reconciliation;
    private final int batchSize;

    public OpsToolExecutionCompletionReconciliationJob(
            ToolExecutionCompletionReconciliationService reconciliation,
            @Value("${orbisops.tool-execution.completion-reconciliation.batch-size:100}")
            int batchSize) {
        if (reconciliation == null) {
            throw new IllegalArgumentException("TOOL_COMPLETION_RECONCILIATION_REQUIRED");
        }
        this.reconciliation = reconciliation;
        this.batchSize = Math.max(1, Math.min(batchSize, 200));
    }

    @Scheduled(fixedDelayString =
            "${orbisops.tool-execution.completion-reconciliation.fixed-delay-ms:10000}")
    public void reconcile() {
        ToolExecutionCompletionReconciliationService.ReconciliationOutcome outcome =
                reconciliation.reconcile(batchSize);
        if (outcome.scanned() > 0) {
            log.info("工具执行完成投影对账完成 outcome={}", outcome);
        }
    }
}
