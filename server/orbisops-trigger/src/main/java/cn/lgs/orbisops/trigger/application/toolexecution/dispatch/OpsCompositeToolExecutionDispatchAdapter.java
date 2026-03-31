package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.toolexecution.ToolBindingResolver;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionDispatchPort;
import cn.lgs.orbisops.application.toolexecution.ToolInvoker;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.ToolBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class OpsCompositeToolExecutionDispatchAdapter implements ToolExecutionDispatchPort {

    private final ToolBindingResolver bindingResolver;
    private final List<ToolInvoker<?>> invokers;

    @Autowired
    public OpsCompositeToolExecutionDispatchAdapter(
            ToolBindingResolver bindingResolver,
            List<ToolInvoker<?>> invokers) {
        if (bindingResolver == null) throw new IllegalArgumentException("TOOL_BINDING_RESOLVER_REQUIRED");
        this.bindingResolver = bindingResolver;
        this.invokers = invokers == null ? List.of() : invokers.stream()
                .sorted(Comparator.comparingInt(ToolInvoker::order))
                .toList();
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        ToolBinding binding = bindingResolver.resolve(target);
        return invokers.stream()
                .filter(invoker -> invoker.supports(target, binding))
                .findFirst()
                .orElseThrow(() -> new SecurityException(
                        "TOOL_BINDING_INVOKER_NOT_IMPLEMENTED："
                                + binding.providerType() + "/" + binding.providerId()))
                .invoke(target, binding, request);
    }

}
