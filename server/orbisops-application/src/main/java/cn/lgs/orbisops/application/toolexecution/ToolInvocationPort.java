package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocation;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocationResult;

/** Single provider-neutral entry point for every Agent-callable Tool. */
public interface ToolInvocationPort {

    ToolInvocationResult invoke(ToolInvocation invocation);
}
