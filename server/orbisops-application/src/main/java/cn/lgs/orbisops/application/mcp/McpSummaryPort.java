package cn.lgs.orbisops.application.mcp;

import java.util.Map;

public interface McpSummaryPort {

    Map<String, Object> summary(String projectId);

    Map<String, Object> rebuildSummary(String projectId);
}
