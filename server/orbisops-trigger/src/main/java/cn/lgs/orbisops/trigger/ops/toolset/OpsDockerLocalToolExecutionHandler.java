package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalHostApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "orbisops.external-local",
        name = "docker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class OpsDockerLocalToolExecutionHandler extends AbstractOpsSingleLocalToolExecutionHandler {
    private final OpsLocalDockerAdapter adapter;

    @org.springframework.beans.factory.annotation.Autowired
    public OpsDockerLocalToolExecutionHandler(LocalHostApplicationService service, OpsLocalAdapterSettings settings) {
        this(new OpsLocalDockerAdapter(service, settings));
    }

    OpsDockerLocalToolExecutionHandler(OpsLocalDockerAdapter adapter) {
        super("LOCAL_DOCKER");
        if (adapter == null) throw new IllegalArgumentException("LOCAL_DOCKER_ADAPTER_REQUIRED");
        this.adapter = adapter;
    }

    @Override
    protected Map<String, Object> executeRequired(String toolName, OpsLocalToolArguments arguments) {
        return adapter.execute(toolName, arguments);
    }
}
