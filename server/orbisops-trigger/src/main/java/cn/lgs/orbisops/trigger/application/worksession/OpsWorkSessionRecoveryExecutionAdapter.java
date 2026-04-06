package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryExecutionPort;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** Runtime ACL that schedules one durable recovery through the normal Work Session lifecycle. */
@Component
@Slf4j
public final class OpsWorkSessionRecoveryExecutionAdapter
        implements WorkSessionRecoveryExecutionPort {

    private final OpsWorkSessionRunAdapter runs;
    private final ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> execution;
    private final Executor executor;

    public OpsWorkSessionRecoveryExecutionAdapter(
            OpsWorkSessionRunAdapter runs,
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> execution,
            @Qualifier("opsSubAgentExecutor") Executor executor) {
        if (runs == null || execution == null || executor == null) {
            throw new IllegalArgumentException("WORK_SESSION_RECOVERY_EXECUTION_DEPENDENCIES_REQUIRED");
        }
        this.runs = runs;
        this.execution = execution;
        this.executor = executor;
    }

    @Override
    public RecoveryExecutionOutcome execute(WorkSessionRecoveryPort.RecoveryDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("WORK_SESSION_RECOVERY_DECISION_REQUIRED");
        }
        if (!"RECOVERABLE".equalsIgnoreCase(decision.status())) {
            return RecoveryExecutionOutcome.skipped(decision);
        }
        OpsAgentChatRequest request = runs.recoveryRequest(
                decision.runId(), decision.projectId(), decision.attemptId());
        Map<String, Object> metadata = request.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getMetadata());
        metadata.put("automaticRecovery", true);
        metadata.put("recoveryReasonCode", decision.reasonCode());
        metadata.put("recoveryExpiredAttemptId", decision.attemptId());
        request.setMetadata(metadata);
        executor.execute(() -> {
            try {
                execution.execute(request);
            } catch (RuntimeException error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage();
                try {
                    runs.finish(request, "FAILED",
                            "WORK_SESSION_RECOVERY_EXECUTION_FAILED:" + message);
                } catch (RuntimeException finalizationError) {
                    error.addSuppressed(finalizationError);
                }
                log.warn("Work Session 自动恢复执行失败，runId={} reason={}",
                        decision.runId(), message, error);
            }
        });
        return RecoveryExecutionOutcome.scheduled(decision);
    }
}
