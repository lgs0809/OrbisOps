package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.Map;
import java.util.Set;

public interface OpsLocalToolExecutionHandler {

    Set<String> supportedAdapterTypes();

    Map<String, Object> execute(
            String adapterType,
            String toolName,
            OpsLocalToolArguments arguments);
}
