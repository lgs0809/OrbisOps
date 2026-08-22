package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalRedisApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "orbisops.external-local",
        name = "redis-enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class OpsRedisLocalToolExecutionHandler extends AbstractOpsSingleLocalToolExecutionHandler {
    private final OpsLocalRedisAdapter adapter;

    @org.springframework.beans.factory.annotation.Autowired
    public OpsRedisLocalToolExecutionHandler(LocalRedisApplicationService service, OpsLocalAdapterSettings settings) {
        this(new OpsLocalRedisAdapter(service, settings));
    }

    OpsRedisLocalToolExecutionHandler(OpsLocalRedisAdapter adapter) {
        super("LOCAL_REDIS");
        if (adapter == null) throw new IllegalArgumentException("LOCAL_REDIS_ADAPTER_REQUIRED");
        this.adapter = adapter;
    }

    @Override
    protected Map<String, Object> executeRequired(String toolName, OpsLocalToolArguments arguments) {
        return adapter.execute(toolName, arguments);
    }
}
