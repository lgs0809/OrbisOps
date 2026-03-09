package cn.lgs.orbisops.application.toolset;

import java.util.Map;

public interface ToolExecutionPort {
    Map<String, Object> execute(Map<String, Object> request, String actor);
}
