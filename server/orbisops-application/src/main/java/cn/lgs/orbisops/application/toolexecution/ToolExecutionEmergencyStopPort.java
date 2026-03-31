package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;

import java.time.Instant;

/** Global/project operational kill switch checked immediately before any side effect. */
@FunctionalInterface
public interface ToolExecutionEmergencyStopPort {

    boolean blocks(ToolExecutionRequest request, ToolExecutionTarget target);

    default StopState set(String projectId, boolean active, String reason, String actor) {
        throw new UnsupportedOperationException("TOOL_EXECUTION_EMERGENCY_STOP_CONTROL_UNAVAILABLE");
    }

    default StopStatus status(String projectId) {
        throw new UnsupportedOperationException("TOOL_EXECUTION_EMERGENCY_STOP_CONTROL_UNAVAILABLE");
    }

    static ToolExecutionEmergencyStopPort disabled() {
        return (request, target) -> false;
    }

    record StopState(boolean active, String reason, String actor, Instant updatedAt) {
    }

    record StopStatus(
            String projectId,
            boolean active,
            boolean globalStop,
            boolean stateAvailable,
            String reason,
            String actor,
            String updatedAt) {
    }
}
