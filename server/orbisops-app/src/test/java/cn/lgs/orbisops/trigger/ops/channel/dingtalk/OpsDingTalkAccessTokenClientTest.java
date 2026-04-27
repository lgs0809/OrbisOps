package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpsDingTalkAccessTokenClientTest {

    @Test
    void usesCorpScopedCredentialsFlowAndCachesUsableToken() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.resolve("credential-ref")).thenReturn("[REDACTED]");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.dingtalk.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/corp-1/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("client_id")))
                .andExpect(content().string(containsString("client-1")))
                .andExpect(content().string(containsString("grant_type")))
                .andExpect(content().string(containsString("client_credentials")))
                .andRespond(withSuccess("""
                        {"access_token":"access-1","expires_in":7200}
                        """, MediaType.APPLICATION_JSON));
        OpsDingTalkAccessTokenClient client = new OpsDingTalkAccessTokenClient(secrets, builder.build());
        OpsDingTalkChannelConfiguration configuration = configuration();

        assertEquals("access-1", client.token(configuration));
        assertEquals("access-1", client.token(configuration));
        server.verify();
    }

    private OpsDingTalkChannelConfiguration configuration() {
        return new OpsDingTalkChannelConfiguration(
                "channel-1", "project-1", "credential-ref", "client-1", "corp-1", "robot-1", "template-1.schema",
                ChannelConnectionMode.LONG_CONNECTION);
    }
}
