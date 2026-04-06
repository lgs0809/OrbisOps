package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.SemanticMemoryClearApplicationService;
import cn.lgs.orbisops.application.memory.SemanticMemoryClearPort;
import org.springframework.stereotype.Component;

/** Trigger adapter exposing semantic clear to the session-clear use case. */
@Component
public class OpsSemanticMemoryClearAdapter implements SemanticMemoryClearPort {

    private final SemanticMemoryClearApplicationService clearService;

    public OpsSemanticMemoryClearAdapter(SemanticMemoryClearApplicationService clearService) {
        this.clearService = clearService;
    }

    @Override
    public void clear(String sessionId) {
        clearService.clear(sessionId);
    }
}
