package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared HTTP transport for OpenAI-compatible model gateways.
 */
public final class OpsModelHttpClientFactory {

    private OpsModelHttpClientFactory() {
    }

    public static RestClient.Builder restClientBuilder(int connectTimeoutSeconds, int readTimeoutSeconds) {
        return RestClient.builder()
                .requestFactory(requestFactory(connectTimeoutSeconds, readTimeoutSeconds))
                .messageConverters(converters -> converters.stream()
                        .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                        .map(MappingJackson2HttpMessageConverter.class::cast)
                        .forEach(OpsModelHttpClientFactory::supportCompatibleGatewayMediaTypes));
    }

    public static JdkClientHttpRequestFactory requestFactory(int connectTimeoutSeconds, int readTimeoutSeconds) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(5, readTimeoutSeconds)));
        return requestFactory;
    }

    private static void supportCompatibleGatewayMediaTypes(MappingJackson2HttpMessageConverter converter) {
        List<MediaType> supportedMediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
        if (!supportedMediaTypes.contains(MediaType.APPLICATION_OCTET_STREAM)) {
            supportedMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        }
        if (!supportedMediaTypes.contains(MediaType.TEXT_EVENT_STREAM)) {
            supportedMediaTypes.add(MediaType.TEXT_EVENT_STREAM);
        }
        converter.setSupportedMediaTypes(supportedMediaTypes);
    }
}
