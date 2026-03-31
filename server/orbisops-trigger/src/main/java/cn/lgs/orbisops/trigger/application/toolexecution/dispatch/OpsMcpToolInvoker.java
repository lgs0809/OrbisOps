package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.toolexecution.ToolInvoker;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.McpToolBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class OpsMcpToolInvoker implements ToolInvoker<McpToolBinding> {

    private final OpsMcpToolExecutionDispatchHandler handler;
    private final List<OpsToolInvocationContract> contracts;

    public OpsMcpToolInvoker(OpsMcpToolExecutionDispatchHandler handler) {
        this(handler, List.of());
    }

    @Autowired
    public OpsMcpToolInvoker(
            OpsMcpToolExecutionDispatchHandler handler,
            List<OpsToolInvocationContract> contracts) {
        if (handler == null) throw new IllegalArgumentException("MCP_TOOL_HANDLER_REQUIRED");
        this.handler = handler;
        this.contracts = contracts == null ? List.of() : List.copyOf(contracts);
    }

    @Override
    public Class<McpToolBinding> bindingType() {
        return McpToolBinding.class;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean supportsTyped(ToolExecutionTarget target, McpToolBinding binding) {
        return handler.supports(target);
    }

    @Override
    public Object invokeTyped(
            ToolExecutionTarget target,
            McpToolBinding binding,
            ToolExecutionRequest request) {
        OpsToolInvocationContract contract = contracts.stream()
                .filter(item -> item.supports(target))
                .findFirst()
                .orElse(null);
        ToolExecutionRequest prepared = contract == null
                ? request
                : contract.prepare(target, request);
        Object output = handler.dispatch(target, prepared);
        return contract == null
                ? output
                : contract.validateOutput(target, prepared, output);
    }
}
