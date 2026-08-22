package cn.lgs.orbisops.trigger.ops.toolset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "orbisops.external-local",
        name = "prometheus-enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class OpsPrometheusLocalToolExecutionHandler extends AbstractOpsSingleLocalToolExecutionHandler {
    private final OpsLocalPrometheusAdapter adapter;

    @org.springframework.beans.factory.annotation.Autowired
    public OpsPrometheusLocalToolExecutionHandler(OpsLocalAdapterSettings settings) {
        this(new OpsLocalPrometheusAdapter(settings, new OpsLocalHttpTransport(settings)));
    }

    OpsPrometheusLocalToolExecutionHandler(OpsLocalPrometheusAdapter adapter) {
        super("LOCAL_PROMETHEUS");
        if (adapter == null) throw new IllegalArgumentException("LOCAL_PROMETHEUS_ADAPTER_REQUIRED");
        this.adapter = adapter;
    }

    @Override
    protected Map<String, Object> executeRequired(String toolName, OpsLocalToolArguments arguments) {
        return adapter.execute(toolName, arguments);
    }
}
