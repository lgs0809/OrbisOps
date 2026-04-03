package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
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
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client) {
            @Override
            public org.springframework.http.client.ClientHttpRequest createRequest(java.net.URI uri,
                    org.springframework.http.HttpMethod method) throws java.io.IOException {
                long remaining = OpsNodeDeadlineContext.remainingMillis(
                        Duration.ofSeconds(Math.max(1, readTimeoutSeconds)).toMillis());
                if (remaining <= 0 || Thread.currentThread().isInterrupted())
                    throw new IllegalStateException("MODEL_CALL_DEADLINE_EXHAUSTED");
                var bounded = new JdkClientHttpRequestFactory(client);
                bounded.setReadTimeout(Duration.ofMillis(remaining));
                return new OpsModelDeadlineHttpRequest(bounded.createRequest(uri, method), remaining);
            }
        };
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(5, readTimeoutSeconds)));
        return requestFactory;
    }

    public static WebClient.Builder webClientBuilder(int connectTimeoutSeconds, int readTimeoutSeconds) {
        var connector = new JdkClientHttpConnector(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds))).build());
        connector.setReadTimeout(Duration.ofSeconds(Math.max(1, readTimeoutSeconds)));
        return WebClient.builder().clientConnector(connector)
                // Measure transport inactivity before SSE decoding / SDK tool-call aggregation.
                // A long, continuously arriving argument stream is not an idle connection.
                .filter((request, next) -> next.exchange(request).map(response -> response.mutate()
                        .body(body -> body.timeout(Duration.ofSeconds(Math.max(1, readTimeoutSeconds))))
                        .build()));
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
