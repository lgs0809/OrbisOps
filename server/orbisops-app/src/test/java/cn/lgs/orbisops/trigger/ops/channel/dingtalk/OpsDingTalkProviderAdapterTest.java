package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsDingTalkProviderAdapterTest {

    private OpsSecretResolver secrets;
    private OpsDingTalkChannelConnectionDriver driver;
    private OpsDingTalkProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        secrets = mock(OpsSecretResolver.class);
        driver = mock(OpsDingTalkChannelConnectionDriver.class);
        adapter = new OpsDingTalkProviderAdapter(secrets, driver);
    }

    @Test
    void exposesOnlyCapabilitiesImplementedByCurrentStreamTransport() {
        assertTrue(adapter.capabilities().supports(ChannelCapability.INBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.OUTBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION));
        assertTrue(adapter.capabilities().supports(ChannelCapability.REPLY_TO_INBOUND));
        assertTrue(adapter.capabilities().supports(ChannelCapability.DIRECT_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.GROUP_MESSAGES));
        assertTrue(adapter.capabilities().supports(ChannelCapability.MESSAGE_UPDATE));
        assertTrue(adapter.capabilities().supports(ChannelCapability.INTERACTIVE_ACTIONS));
        assertTrue(adapter.capabilities().supports(ChannelCapability.PROACTIVE_PUSH));
        assertFalse(adapter.capabilities().supports(ChannelCapability.ATTACHMENTS));
        assertFalse(adapter.capabilities().supports(ChannelCapability.WEBHOOK));
    }

    @Test
    void configurationDefaultsToStreamModeAndRequiresSecretReference() {
        when(secrets.isReference("${env:DINGTALK_CLIENT_SECRET}")).thenReturn(true);
        OpsDingTalkChannelConfiguration configuration = adapter.configuration(channel(
                "${env:DINGTALK_CLIENT_SECRET}", Map.of(
                        "clientId", "ding-client-1",
                        "corpId", "corp-1",
                        "robotCode", "robot-1",
                        "cardTemplateId", "template-1.schema")));

        adapter.validateConfiguration(configuration);

        assertEquals("ding-client-1", configuration.clientId());
        assertEquals(ChannelConnectionMode.LONG_CONNECTION, configuration.connectionMode());
    }

    @Test
    void unresolvedClientSecretIsBlockedExternalInsteadOfReady() {
        when(secrets.isReference("${env:DINGTALK_CLIENT_SECRET}")).thenReturn(true);
        when(secrets.resolve("${env:DINGTALK_CLIENT_SECRET}")).thenReturn("");
        OpsDingTalkChannelConfiguration configuration = adapter.configuration(channel(
                "${env:DINGTALK_CLIENT_SECRET}", Map.of(
                        "clientId", "ding-client-1",
                        "corpId", "corp-1",
                        "robotCode", "robot-1",
                        "cardTemplateId", "template-1.schema")));

        ChannelHealthSnapshot health = adapter.preflight(configuration);

        assertEquals(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL, health.status());
        assertEquals("DINGTALK_CLIENT_SECRET_UNAVAILABLE", health.reasonCode());
    }

    @Test
    void driverFallsBackToProactiveDeliveryWhenCardAndLiveReplyContextAreUnavailable() {
        OpsDingTalkProactiveMessageClient proactive = mock(OpsDingTalkProactiveMessageClient.class);
        OpsDingTalkCardClient cards = mock(OpsDingTalkCardClient.class);
        OpsDingTalkChannelConnectionDriver realDriver = new OpsDingTalkChannelConnectionDriver(
                secrets, mock(ReceiveChannelMessageUseCase.class), proactive, cards,
                mock(cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher.class));
        OpsDingTalkChannelConfiguration configuration = new OpsDingTalkChannelConfiguration(
                "channel-ding", "project-1", "${env:DINGTALK_CLIENT_SECRET}", "ding-client-1",
                "corp-1", "robot-1", "template-1.schema", ChannelConnectionMode.LONG_CONNECTION);
        ChannelConversationRef conversation = new ChannelConversationRef(
                "group:cid-no-context", ChannelConversationRef.ConversationKind.GROUP);
        ChannelOutboundMessage outbound = new ChannelOutboundMessage(
                conversation, ChannelRichContent.text("hello"), List.of(), null);
        var expected = new cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt(
                true, "DELIVERED", "process-1", null, java.time.Instant.now());
        when(cards.create(configuration, outbound)).thenThrow(new IllegalStateException("card unavailable"));
        when(proactive.send(configuration, outbound)).thenReturn(expected);

        var actual = realDriver.send(configuration, outbound);

        assertEquals(expected, actual);
    }

    private ChannelRecord channel(String credentialRef, Map<String, Object> config) {
        return new ChannelRecord("channel-ding", "project-1", ExecutionBinding.react(), "DingTalk", "DINGTALK",
                credentialRef, config, ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE, "admin", null, null);
    }
}
