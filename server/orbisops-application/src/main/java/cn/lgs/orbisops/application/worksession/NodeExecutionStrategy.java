package cn.lgs.orbisops.application.worksession;

import java.util.Set;

public interface NodeExecutionStrategy<N, C, R> {

    String strategyId();

    Set<String> supportedNodeTypes();

    R execute(N node, C context);
}
