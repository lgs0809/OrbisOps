package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.toolexecution.ToolInvoker;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.InternalToolBinding;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Typed Internal invoker backed by the local and built-in Handler registry. */
@Component
public final class OpsInternalToolInvoker implements ToolInvoker<InternalToolBinding> {

    private final List<OpsToolExecutionDispatchHandler> handlers;

    public OpsInternalToolInvoker(List<OpsToolExecutionDispatchHandler> handlers) {
        this.handlers = handlers == null ? List.of() : handlers.stream()
                .filter(handler -> !(handler instanceof OpsMcpToolExecutionDispatchHandler))
                .sorted(Comparator.comparingInt(OpsToolExecutionDispatchHandler::order))
                .toList();
    }

    @Override
    public Class<InternalToolBinding> bindingType() {
        return InternalToolBinding.class;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supportsTyped(ToolExecutionTarget target, InternalToolBinding binding) {
        return handler(target) != null;
    }

    @Override
    public Object invokeTyped(
            ToolExecutionTarget target,
            InternalToolBinding binding,
            ToolExecutionRequest request) {
        OpsToolExecutionDispatchHandler handler = handler(target);
        if (handler == null) {
            throw new SecurityException(
                    "INTERNAL_TOOL_HANDLER_NOT_IMPLEMENTED：" + binding.handlerId());
        }
        return handler.dispatch(target, request);
    }

    private OpsToolExecutionDispatchHandler handler(ToolExecutionTarget target) {
        if (target == null) return null;
        return handlers.stream()
                .filter(handler -> handler.supports(target))
                .findFirst()
                .orElse(null);
    }
}
