package cn.lgs.orbisops.trigger.ops.channel.wechat;

import cn.lgs.orbisops.application.channel.ChannelOutboundMetadata;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpsWeChatOfficialAccountClientTest {

    @Test
    void obtainsAndCachesOfficialAccessToken() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.resolve("credential-ref")).thenReturn("[REDACTED_SECRET]");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("https://api.weixin.qq.com/cgi-bin/token?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"access_token\":\"[REDACTED_SECRET]\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        OpsWeChatOfficialAccountClient client = new OpsWeChatOfficialAccountClient(secrets, builder.build());
        OpsWeChatChannelConfiguration configuration = configuration();

        assertEquals("[REDACTED_SECRET]", client.token(configuration));
        assertEquals("[REDACTED_SECRET]", client.token(configuration));
        server.verify();
    }

    @Test
    void customerServiceTextSendOnlySucceedsOnExplicitProviderAcceptance() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.resolve("credential-ref")).thenReturn("[REDACTED_SECRET]");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/cgi-bin/token?")))
                .andRespond(withSuccess("{\"access_token\":\"[REDACTED_SECRET]\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/cgi-bin/message/custom/send?access_token=")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("openid-user-1")))
                .andExpect(content().string(containsString("hello from OrbisOps")))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));
        OpsWeChatOfficialAccountClient client = new OpsWeChatOfficialAccountClient(secrets, builder.build());

        var receipt = client.send(configuration(), new ChannelOutboundMessage(
                new ChannelConversationRef("openid-user-1", ChannelConversationRef.ConversationKind.DIRECT),
                ChannelRichContent.text("hello from OrbisOps"),
                List.of(),
                ChannelOutboundMetadata.from(null)));

        assertTrue(receipt.delivered());
        assertEquals("DELIVERED", receipt.status());
        assertEquals("", receipt.providerMessageId());
        server.verify();
    }

    private OpsWeChatChannelConfiguration configuration() {
        return new OpsWeChatChannelConfiguration(
                "channel-wechat",
                "project-1",
                "credential-ref",
                "wx-test-app",
                "verification-ref",
                "aes-ref",
                ChannelConnectionMode.WEBHOOK);
    }
}
