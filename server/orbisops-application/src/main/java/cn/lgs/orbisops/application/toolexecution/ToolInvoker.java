package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.ToolBinding;

import java.util.Optional;

/** Protocol-specific invocation SPI below the unified Tool governance coordinator. */
public interface ToolInvoker<B extends ToolBinding> {

    Class<B> bindingType();

    default int order() {
        return 1000;
    }

    boolean supportsTyped(ToolExecutionTarget target, B binding);

    Object invokeTyped(ToolExecutionTarget target, B binding, ToolExecutionRequest request);

    default Optional<Object> reconcileTyped(
            ToolExecutionTarget target,
            B binding,
            String executionKey) {
        return Optional.empty();
    }

    default boolean supports(ToolExecutionTarget target, ToolBinding binding) {
        return bindingType().isInstance(binding)
                && supportsTyped(target, bindingType().cast(binding));
    }

    default Object invoke(
            ToolExecutionTarget target,
            ToolBinding binding,
            ToolExecutionRequest request) {
        if (!bindingType().isInstance(binding)) {
            throw new IllegalArgumentException("TOOL_BINDING_TYPE_MISMATCH");
        }
        return invokeTyped(target, bindingType().cast(binding), request);
    }
}
