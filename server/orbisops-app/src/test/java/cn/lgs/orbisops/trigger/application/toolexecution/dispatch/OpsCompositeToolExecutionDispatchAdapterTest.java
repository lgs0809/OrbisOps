package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.toolexecution.ToolBindingResolver;
import cn.lgs.orbisops.application.toolexecution.ToolInvoker;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.InternalToolBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsCompositeToolExecutionDispatchAdapterTest {

    @Test
    void shouldResolveBindingBeforeSelectingInvoker() {
        ToolBindingResolver resolver = ToolExecutionTarget::binding;
        ToolInvoker<InternalToolBinding> invoker = internalInvoker(true, "invoked");

        Object output = new OpsCompositeToolExecutionDispatchAdapter(
                resolver, List.of(invoker))
                .dispatch(target(), request());

        assertEquals("invoked", output);
    }

    @Test
    void shouldFailClosedWithoutBindingInvoker() {
        ToolBindingResolver resolver = ToolExecutionTarget::binding;
        ToolInvoker<InternalToolBinding> unsupported = internalInvoker(false, "never");

        assertThrows(SecurityException.class, () ->
                new OpsCompositeToolExecutionDispatchAdapter(
                        resolver, List.of(unsupported))
                        .dispatch(target(), request()));
    }

    private ToolInvoker<InternalToolBinding> internalInvoker(
            boolean supports,
            Object output) {
        return new ToolInvoker<>() {
            @Override public Class<InternalToolBinding> bindingType() { return InternalToolBinding.class; }
            @Override public boolean supportsTyped(
                    ToolExecutionTarget target,
                    InternalToolBinding binding) { return supports; }
            @Override public Object invokeTyped(
                    ToolExecutionTarget target,
                    InternalToolBinding binding,
                    ToolExecutionRequest request) { return output; }
        };
    }


    private ToolExecutionTarget target() {
        return new ToolExecutionTarget(
                "code.repair", "code_bash", "CODE_REPAIR", "MEDIUM",
                false, true, false, false, false);
    }

    private ToolExecutionRequest request() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, Map.of(),
                "session-1", "run-1", Map.of(), Map.of());
    }
}
