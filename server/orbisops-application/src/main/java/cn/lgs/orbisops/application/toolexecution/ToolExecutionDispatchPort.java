package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;

public interface ToolExecutionDispatchPort {

    Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request);
}
