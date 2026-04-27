package cn.lgs.orbisops.trigger.ops.channel.wecom;

import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundPacket;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsWeComProviderAdapterTest {

    private OpsSecretResolver secrets;
    private OpsWeComChannelConnectionDriver driver;
    private OpsWeComProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        secrets = mock(OpsSecretResolver.class);
        driver = mock(OpsWeComChannelConnectionDriver.class);
        adapter = new OpsWeComProviderAdapter(secrets, driver);
    }

    @Test
    void exposesLongConnectionInteractiveReplyAndProactiveCapabilities() {
        assertTrue(adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION));
        assertTrue(adapter.capabilities().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertTrue(adapter.capabilities().supports(ChannelCapability.PROACTIVE_PUSH));
        assertTrue(adapter.capabilities().supports(ChannelCapability.REPLY_TO_INBOUND));
        assertFalse(adapter.capabilities().supports(ChannelCapability.MESSAGE_UPDATE));
    }

    @Test
    void unresolvedBotSecretIsBlockedExternal() {
        when(secrets.isReference("${env:WECOM_SECRET}")).thenReturn(true);
        when(secrets.resolve("${env:WECOM_SECRET}")).thenReturn("");
        var configuration = adapter.configuration(channel());

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL, health.status());
        assertEquals("WECOM_BOT_SECRET_UNAVAILABLE", health.reasonCode());
    }

    @Test
    void parsesInteractiveCallbackThroughTypedProviderContract() {
        when(secrets.isReference("${env:WECOM_SECRET}")).thenReturn(true);
        String raw = """
                {"cmd":"aibot_event_callback","headers":{"req_id":"event-1"},"body":{
                  "msgid":"msg-card-1","chatid":"room-1","chattype":"group","from":{"userid":"user-1"},
                  "create_time":1710000000,"msgtype":"event","event":{"eventtype":"template_card_event",
                  "template_card_event":{"event_key":"opaque-action-123","task_id":"task-1"}}}}
                """;
        var packet = new ChannelInboundPacket("application/json", raw.getBytes(StandardCharsets.UTF_8), List.of());

        var action = adapter.parseInteractiveAction(adapter.configuration(channel()), packet).orElseThrow();

        assertEquals("opaque-action-123", action.action().opaqueActionToken());
        assertEquals("user-1", action.actor().externalPrincipalId());
    }

    private ChannelRecord channel() {
        return new ChannelRecord("channel-wecom", "project-1", ExecutionBinding.react(), "WeCom", "WECOM",
                "${env:WECOM_SECRET}", Map.of("botId", "bot-1"), ChannelAccessPolicy.DENY_UNKNOWN,
                ChannelStatus.ACTIVE, "admin", null, null);
    }
}
