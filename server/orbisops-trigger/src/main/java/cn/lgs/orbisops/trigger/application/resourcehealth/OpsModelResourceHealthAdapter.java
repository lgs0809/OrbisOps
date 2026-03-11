package cn.lgs.orbisops.trigger.application.resourcehealth;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.application.model.ModelAvailabilitySnapshot;
import cn.lgs.orbisops.application.resourcehealth.ModelResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Trigger adapter exposing model runtime availability to the application probe boundary. */
@Component
public class OpsModelResourceHealthAdapter
        implements ModelResourceHealthProbePort {

    private final ModelAvailabilityPort availability;

    public OpsModelResourceHealthAdapter(ModelAvailabilityPort availability) {
        this.availability = availability;
    }

    @Override
    public ResourceHealthCheck probe() {
        ModelAvailabilitySnapshot snapshot = availability.snapshot();
        boolean healthy = snapshot != null
                && (snapshot.chatAvailable() || snapshot.embeddingAvailable());
        String message = snapshot == null || snapshot.message() == null
                ? healthy ? "模型配置可用" : "模型未完整配置"
                : snapshot.message();
        Map<String, Object> detail = snapshot == null ? Map.of() : Map.ofEntries(
                Map.entry("modelCallsEnabled", snapshot.modelCallsEnabled()),
                Map.entry("chatAvailable", snapshot.chatAvailable()),
                Map.entry("embeddingAvailable", snapshot.embeddingAvailable()),
                Map.entry("rerankAvailable", snapshot.rerankAvailable()),
                Map.entry("openAiApiKeyUsable", snapshot.openAiApiKeyUsable()),
                Map.entry("embeddingApiKeyUsable", snapshot.embeddingApiKeyUsable()),
                Map.entry("rerankApiKeyUsable", snapshot.rerankApiKeyUsable()),
                Map.entry("embeddingLocalEndpointReady", snapshot.embeddingLocalEndpointReady()),
                Map.entry("rerankLocalEndpointReady", snapshot.rerankLocalEndpointReady()),
                Map.entry("message", message));
        return new ResourceHealthCheck(
                "model",
                "OpenAI/兼容模型",
                "spring.ai.openai",
                healthy,
                message,
                Map.of("detail", detail));
    }
}
