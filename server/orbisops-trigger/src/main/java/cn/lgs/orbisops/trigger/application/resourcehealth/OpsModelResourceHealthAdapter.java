package cn.lgs.orbisops.trigger.application.resourcehealth;

import cn.lgs.orbisops.application.resourcehealth.ModelResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import cn.lgs.orbisops.domain.agent.service.model.AiModelAvailability;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Trigger adapter exposing model runtime availability to the application probe boundary. */
@Component
public class OpsModelResourceHealthAdapter
        implements ModelResourceHealthProbePort {

    private final AiModelAvailability availability;

    public OpsModelResourceHealthAdapter(AiModelAvailability availability) {
        this.availability = availability;
    }

    @Override
    public ResourceHealthCheck probe() {
        Map<String, Object> status = availability.status();
        Map<String, Object> detail = status == null ? Map.of() : status;
        boolean healthy = Boolean.TRUE.equals(detail.get("chatAvailable"))
                || Boolean.TRUE.equals(detail.get("embeddingAvailable"));
        String message = String.valueOf(detail.getOrDefault(
                "message",
                healthy ? "模型配置可用" : "模型未完整配置"));
        return new ResourceHealthCheck(
                "model",
                "OpenAI/兼容模型",
                "spring.ai.openai",
                healthy,
                message,
                Map.of("detail", detail));
    }
}
