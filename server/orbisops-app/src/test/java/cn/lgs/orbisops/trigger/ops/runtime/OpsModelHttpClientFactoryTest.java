package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpsModelHttpClientFactoryTest {

    @Test
    void shouldUseJdkHttpClientForCompatibleModelGateways() {
        assertInstanceOf(JdkClientHttpRequestFactory.class,
                OpsModelHttpClientFactory.requestFactory(5, 45));
        assertNotNull(OpsModelHttpClientFactory.restClientBuilder(5, 45));
    }
}
