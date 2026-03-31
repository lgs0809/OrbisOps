package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;

public interface OpsToolExecutionDispatchHandler {

    String handlerId();

    int order();

    boolean supports(ToolExecutionTarget target);

    Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request);
}
