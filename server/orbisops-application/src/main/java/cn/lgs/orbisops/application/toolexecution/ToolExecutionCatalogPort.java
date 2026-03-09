package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResolution;

public interface ToolExecutionCatalogPort {

    ToolExecutionResolution resolve(ToolExecutionRequest request);
}
