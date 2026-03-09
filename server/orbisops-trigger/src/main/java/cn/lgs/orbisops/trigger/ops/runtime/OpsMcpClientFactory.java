package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson.JacksonJsonSchemaValidatorSupplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

@Service
public class OpsMcpClientFactory {

    private final OpsSecretResolver secretResolver;
    private final OpsMcpTransportSecurityPolicy securityPolicy;
    private final McpJsonMapper jsonMapper;
    private final JsonSchemaValidator jsonSchemaValidator;

    public OpsMcpClientFactory(OpsSecretResolver secretResolver,
                               OpsMcpTransportSecurityPolicy securityPolicy) {
        this.secretResolver = secretResolver;
        this.securityPolicy = securityPolicy;
        this.jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        this.jsonSchemaValidator = new JacksonJsonSchemaValidatorSupplier().get();
    }

    public McpSyncClient create(OpsMcpServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MCP_CLIENT_CONFIG_REQUIRED");
        }
        String transport = securityPolicy.normalizeTransport(config.getTransport());
        securityPolicy.assertTransportAllowed(config, transport);
        int timeoutSeconds = securityPolicy.normalizeTimeout(config.getTimeoutSeconds());
        if ("sse".equals(transport)) {
            RemoteAddress address = normalizeSseAddress(secretResolver.resolve(config.getUrl()));
            securityPolicy.assertRemoteAddressAllowed(config, address.baseUri());
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            securityPolicy.safeHeaders(config, secretResolver.resolveMap(config.getHeaders()))
                    .forEach(requestBuilder::header);
            HttpClientSseClientTransport sseTransport = HttpClientSseClientTransport.builder(address.baseUri())
                    .sseEndpoint(address.endpoint())
                    .requestBuilder(requestBuilder)
                    .jsonMapper(jsonMapper)
                    .build();
            return McpClient.sync(sseTransport)
                    .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                    .jsonSchemaValidator(jsonSchemaValidator)
                    .build();
        }
        if ("streamable-http".equals(transport)) {
            RemoteAddress address = normalizeStreamableHttpAddress(secretResolver.resolve(config.getUrl()));
            securityPolicy.assertRemoteAddressAllowed(config, address.baseUri());
            WebClient.Builder webClientBuilder = WebClient.builder()
                    .baseUrl(address.baseUri())
                    .defaultHeaders(headers -> securityPolicy.safeHeaders(config, secretResolver.resolveMap(config.getHeaders()))
                            .forEach(headers::add));
            WebClientStreamableHttpTransport httpTransport = WebClientStreamableHttpTransport.builder(webClientBuilder)
                    .endpoint(address.endpoint())
                    .jsonMapper(jsonMapper)
                    .openConnectionOnStartup(true)
                    .build();
            return McpClient.sync(httpTransport)
                    .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                    .jsonSchemaValidator(jsonSchemaValidator)
                    .build();
        }
        if (!"stdio".equals(transport)) {
            throw new IllegalArgumentException("当前节点 MCP 仅支持 stdio/sse/streamable-http，server=" + config.getName() + ", transport=" + config.getTransport());
        }
        if (!StringUtils.hasText(config.getCommand())) {
            throw new IllegalArgumentException("stdio MCP 缺少 command，server=" + config.getName());
        }
        String command = secretResolver.resolve(config.getCommand()).trim();
        if (!StringUtils.hasText(command)) {
            throw new IllegalArgumentException("stdio MCP command 占位符解析后为空，server=" + config.getName());
        }
        List<String> args = safeArgs(config);
        securityPolicy.assertStdioAllowed(config, command);
        securityPolicy.assertArgsAllowed(config, args);
        ServerParameters parameters = ServerParameters.builder(command)
                .args(args)
                .env(securityPolicy.safeEnv(config, secretResolver.resolveMap(config.getEnv())))
                .build();
        return McpClient.sync(new StdioClientTransport(parameters, jsonMapper))
                .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                .jsonSchemaValidator(jsonSchemaValidator)
                .build();
    }

    public String cacheKey(OpsMcpServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MCP_CLIENT_CONFIG_REQUIRED");
        }
        return String.join("|",
                value(config.getName()),
                value(config.getTransport()),
                value(config.getCommand()),
                value(config.getUrl()),
                String.valueOf(config.getArgs()),
                String.valueOf(secretResolver.redactMap(config.getEnv())),
                String.valueOf(secretResolver.redactMap(config.getHeaders())),
                String.valueOf(config.getToolCapabilities()),
                String.valueOf(config.getAllowedTools()),
                String.valueOf(config.getNotificationTools()),
                String.valueOf(config.getBlockedTools()));
    }

    private List<String> safeArgs(OpsMcpServerConfig config) {
        if (config.getArgs() == null || config.getArgs().isEmpty()) {
            return List.of();
        }
        return config.getArgs().stream().map(secretResolver::resolve).toList();
    }

    private RemoteAddress normalizeSseAddress(String configuredUrl) {
        if (!StringUtils.hasText(configuredUrl)) {
            throw new IllegalArgumentException("sse MCP 缺少 url");
        }
        String url = configuredUrl.trim();
        int sseIndex = url.indexOf("/sse");
        if (sseIndex >= 0) {
            return new RemoteAddress(stripTrailingSlash(url.substring(0, sseIndex)), normalizeEndpoint(url.substring(sseIndex), "/sse"));
        }
        return new RemoteAddress(stripTrailingSlash(url), "/sse");
    }

    private RemoteAddress normalizeStreamableHttpAddress(String configuredUrl) {
        if (!StringUtils.hasText(configuredUrl)) {
            throw new IllegalArgumentException("streamable-http MCP 缺少 url");
        }
        String url = configuredUrl.trim();
        int mcpIndex = url.indexOf("/mcp");
        if (mcpIndex >= 0) {
            return new RemoteAddress(stripTrailingSlash(url.substring(0, mcpIndex)), normalizeEndpoint(url.substring(mcpIndex), "/mcp"));
        }
        return new RemoteAddress(stripTrailingSlash(url), "/mcp");
    }

    private String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeEndpoint(String endpoint, String fallback) {
        if (!StringUtils.hasText(endpoint)) {
            return fallback;
        }
        String normalized = endpoint.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record RemoteAddress(String baseUri, String endpoint) {
    }
}
