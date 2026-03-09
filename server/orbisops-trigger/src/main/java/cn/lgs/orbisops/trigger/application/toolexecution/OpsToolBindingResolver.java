package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.toolexecution.ToolBindingResolver;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.ToolBinding;
import org.springframework.stereotype.Component;

@Component
public final class OpsToolBindingResolver implements ToolBindingResolver {

    @Override
    public ToolBinding resolve(ToolExecutionTarget target) {
        if (target == null) throw new IllegalArgumentException("TOOL_EXECUTION_TARGET_REQUIRED");
        return target.binding();
    }
}
