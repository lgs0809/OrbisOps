package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalHostApplicationService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class OpsLocalLogToolExecutionHandler extends AbstractOpsSingleLocalToolExecutionHandler {
    private final OpsLocalLogAdapter adapter;

    @org.springframework.beans.factory.annotation.Autowired
    public OpsLocalLogToolExecutionHandler(LocalHostApplicationService service, OpsLocalAdapterSettings settings) {
        this(new OpsLocalLogAdapter(service, settings));
    }

    OpsLocalLogToolExecutionHandler(OpsLocalLogAdapter adapter) {
        super("LOCAL_LOG");
        if (adapter == null) throw new IllegalArgumentException("LOCAL_LOG_ADAPTER_REQUIRED");
        this.adapter = adapter;
    }

    @Override
    protected Map<String, Object> executeRequired(String toolName, OpsLocalToolArguments arguments) {
        return adapter.execute(toolName, arguments);
    }
}
