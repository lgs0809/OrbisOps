package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;

import java.util.Map;

public interface ToolExecutionCheckpointPort {

    void checkpoint(ToolExecutionRequest request, String checkpointType, Map<String, Object> payload);
}
