package cn.lgs.orbisops.trigger.application.toolset;

import cn.lgs.orbisops.application.toolset.ToolExecutionPort;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsToolExecutionAdapter implements ToolExecutionPort {

    private final OpsToolExecutionService service;

    public OpsToolExecutionAdapter(OpsToolExecutionService service) {
        this.service = service;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> request, String actor) {
        return service.execute(request, actor);
    }
}
