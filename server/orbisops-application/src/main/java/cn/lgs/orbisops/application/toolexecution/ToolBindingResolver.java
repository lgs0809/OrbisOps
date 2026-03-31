package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.ToolBinding;

public interface ToolBindingResolver {

    ToolBinding resolve(ToolExecutionTarget target);
}
