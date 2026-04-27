package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsGenericWebhookChannelAdapterTest {

    @Test
    void outboundBridgeCallRequiresStableDeliveryId() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.resolve("${env:CHANNEL_SECRET}")).thenReturn("secret");
        OpsOutboundUrlPolicy policy = mock(OpsOutboundUrlPolicy.class);
        when(policy.validate("https://bridge.example.test/replies"))
                .thenReturn(URI.create("https://bridge.example.test/replies"));
        OpsGenericWebhookChannelAdapter adapter = new OpsGenericWebhookChannelAdapter(secrets, policy);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> adapter.send(
                Map.of("credentialRef", "${env:CHANNEL_SECRET}",
                        "config", Map.of("outboundUrl", "https://bridge.example.test/replies")),
                "room-1", "reply", Map.of("runId", "run-1")));

        assertEquals("CHANNEL_DELIVERY_ID_REQUIRED", error.getMessage());
    }
}
