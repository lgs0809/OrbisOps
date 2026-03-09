package cn.lgs.orbisops.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionDecision;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionTarget;

public interface McpExecutionRouterPort {

    McpExecutionDecision decide(McpExecutionRequest request, McpExecutionTarget target);
}
