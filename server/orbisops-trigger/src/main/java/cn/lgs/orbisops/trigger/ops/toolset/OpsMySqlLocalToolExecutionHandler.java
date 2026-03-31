package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalMySqlApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "orbisops.external-local",
        name = "mysql-enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class OpsMySqlLocalToolExecutionHandler extends AbstractOpsSingleLocalToolExecutionHandler {
    private final OpsLocalMySqlAdapter adapter;

    @org.springframework.beans.factory.annotation.Autowired
    public OpsMySqlLocalToolExecutionHandler(LocalMySqlApplicationService service, OpsLocalAdapterSettings settings) {
        this(new OpsLocalMySqlAdapter(service, settings));
    }

    OpsMySqlLocalToolExecutionHandler(OpsLocalMySqlAdapter adapter) {
        super("LOCAL_MYSQL");
        if (adapter == null) throw new IllegalArgumentException("LOCAL_MYSQL_ADAPTER_REQUIRED");
        this.adapter = adapter;
    }

    @Override
    protected Map<String, Object> executeRequired(String toolName, OpsLocalToolArguments arguments) {
        return adapter.execute(toolName, arguments);
    }
}
