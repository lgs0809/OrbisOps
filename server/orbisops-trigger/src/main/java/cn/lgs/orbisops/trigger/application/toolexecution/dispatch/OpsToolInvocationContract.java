package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;

/** Platform-trusted request/output contract applied around one protocol Invoker. */
public interface OpsToolInvocationContract {

    boolean supports(ToolExecutionTarget target);

    ToolExecutionRequest prepare(
            ToolExecutionTarget target,
            ToolExecutionRequest request);

    Object validateOutput(
            ToolExecutionTarget target,
            ToolExecutionRequest preparedRequest,
            Object output);
}
