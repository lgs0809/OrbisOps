package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionType;

/** Resolves a product execution choice to the exact published runtime definition used for one run. */
public interface ChannelExecutionBindingPort {

    ResolvedExecution resolve(String projectId, ExecutionBinding binding);

    record ResolvedExecution(
            ExecutionType type,
            String definitionId,
            int version,
            String definitionHash) {
        public ResolvedExecution {
            type = type == null ? ExecutionType.NONE : type;
            definitionId = definitionId == null ? "" : definitionId.trim();
            definitionHash = definitionHash == null ? "" : definitionHash.trim();
            if (type != ExecutionType.NONE
                    && (definitionId.isBlank() || version <= 0 || definitionHash.isBlank())) {
                throw new IllegalArgumentException("CHANNEL_EXECUTION_BINDING_INVALID");
            }
        }
    }
}
