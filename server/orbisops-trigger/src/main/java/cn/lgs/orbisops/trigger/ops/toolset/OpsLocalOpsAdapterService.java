package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalHostApplicationService;
import cn.lgs.orbisops.application.toolset.LocalMySqlApplicationService;
import cn.lgs.orbisops.application.toolset.LocalRedisApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Compatibility facade over the immutable local tool execution handler registry. */
@Service
public class OpsLocalOpsAdapterService {

    private final OpsLocalToolExecutionHandlerRegistry handlerRegistry;

    @Autowired
    public OpsLocalOpsAdapterService(OpsLocalToolExecutionHandlerRegistry handlerRegistry) {
        if (handlerRegistry == null) {
            throw new IllegalArgumentException("LOCAL_ADAPTER_HANDLER_REGISTRY_REQUIRED");
        }
        this.handlerRegistry = handlerRegistry;
    }

    public OpsLocalOpsAdapterService(
            LocalHostApplicationService localHostService,
            LocalMySqlApplicationService localMySqlService,
            LocalRedisApplicationService localRedisService) {
        this(localHostService, localMySqlService, localRedisService, OpsLocalAdapterSettings.defaults());
    }

    public OpsLocalOpsAdapterService(
            LocalHostApplicationService localHostService,
            LocalMySqlApplicationService localMySqlService,
            LocalRedisApplicationService localRedisService,
            OpsLocalAdapterSettings settings) {
        this(new OpsLocalToolExecutionHandlerRegistry(List.of(
                new OpsPrometheusLocalToolExecutionHandler(settings),
                new OpsElasticsearchLocalToolExecutionHandler(settings),
                new OpsMySqlLocalToolExecutionHandler(localMySqlService, settings),
                new OpsRedisLocalToolExecutionHandler(localRedisService, settings),
                new OpsDockerLocalToolExecutionHandler(localHostService, settings),
                new OpsLocalLogToolExecutionHandler(localHostService, settings))));
    }

    public Map<String, Object> execute(
            String adapterType,
            String toolName,
            Map<String, Object> arguments) {
        return handlerRegistry.execute(
                adapterType,
                toolName,
                new OpsLocalToolArguments(arguments));
    }

}
