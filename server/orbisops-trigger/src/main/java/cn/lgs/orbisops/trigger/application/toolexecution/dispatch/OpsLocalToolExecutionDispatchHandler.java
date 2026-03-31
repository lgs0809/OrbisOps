package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.ops.toolset.OpsLocalOpsAdapterService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OpsLocalToolExecutionDispatchHandler implements OpsToolExecutionDispatchHandler {

    private final ObjectProvider<OpsLocalOpsAdapterService> localOps;

    public OpsLocalToolExecutionDispatchHandler(ObjectProvider<OpsLocalOpsAdapterService> localOps) {
        this.localOps = localOps;
    }

    @Override
    public String handlerId() {
        return "local-ops";
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return target.adapterType().startsWith("LOCAL_");
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        OpsLocalOpsAdapterService adapter = localOps == null ? null : localOps.getIfAvailable();
        if (adapter == null) {
            throw new IllegalStateException("本地运维 adapter 未初始化：" + target.adapterType());
        }
        return adapter.execute(
                target.adapterType(),
                target.toolName(),
                request.arguments());
    }
}
