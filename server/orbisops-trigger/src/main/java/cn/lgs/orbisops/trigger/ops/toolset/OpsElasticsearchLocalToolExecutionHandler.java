package cn.lgs.orbisops.trigger.ops.toolset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "orbisops.external-local",
        name = "elasticsearch-enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class OpsElasticsearchLocalToolExecutionHandler extends AbstractOpsSingleLocalToolExecutionHandler {
    private final OpsLocalElasticsearchAdapter adapter;

    @org.springframework.beans.factory.annotation.Autowired
    public OpsElasticsearchLocalToolExecutionHandler(OpsLocalAdapterSettings settings) {
        this(new OpsLocalElasticsearchAdapter(settings, new OpsLocalHttpTransport(settings)));
    }

    OpsElasticsearchLocalToolExecutionHandler(OpsLocalElasticsearchAdapter adapter) {
        super("LOCAL_ELASTICSEARCH");
        if (adapter == null) throw new IllegalArgumentException("LOCAL_ELASTICSEARCH_ADAPTER_REQUIRED");
        this.adapter = adapter;
    }

    @Override
    protected Map<String, Object> executeRequired(String toolName, OpsLocalToolArguments arguments) {
        return adapter.execute(toolName, arguments);
    }
}
