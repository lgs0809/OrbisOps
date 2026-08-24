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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

@Service
public class OpsMcpClientFactory {

    private final OpsSecretResolver secretResolver;
    private final OpsMcpTransportSecurityPolicy securityPolicy;
    private final McpJsonMapper jsonMapper;
    private final JsonSchemaValidator jsonSchemaValidator;
    private final java.util.function.ToIntFunction<cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository.Dispatch> dispatchBudget;
    private final java.util.Map<McpSyncClient, java.lang.ref.WeakReference<OpsMcpSingleAttemptTransport>> transports =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public OpsMcpClientFactory(OpsSecretResolver secretResolver,
                               OpsMcpTransportSecurityPolicy securityPolicy) {
        this(secretResolver, securityPolicy, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpsMcpClientFactory(OpsSecretResolver secretResolver, OpsMcpTransportSecurityPolicy securityPolicy,
                              cn.lgs.orbisops.application.workflow.WorkflowToolCallBudgetApplicationService budget) {
        this.secretResolver = secretResolver;
        this.securityPolicy = securityPolicy;
        this.jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        this.jsonSchemaValidator = new JacksonJsonSchemaValidatorSupplier().get();
        this.dispatchBudget = budget == null ? dispatch -> 0 : budget::reserve;
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
            return singleAttempt(sseTransport);
        }
        if ("streamable-http".equals(transport)) {
            RemoteAddress address = normalizeStreamableHttpAddress(secretResolver.resolve(config.getUrl()));
            securityPolicy.assertRemoteAddressAllowed(config, address.baseUri());
            WebClient.Builder webClientBuilder = WebClient.builder()
                    // Decode HTTP content encodings before the SDK's strict JSON-RPC parser.
                    // Neither redirects nor connection resets may silently redispatch a write.
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                            .compress(true).followRedirect(false).disableRetry(true)))
                    .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                    .filter((request, next) -> next.exchange(request).flatMap(response ->
                            request.method() == org.springframework.http.HttpMethod.POST
                                    && response.statusCode().isError() && response.statusCode().value() != 404
                                    ? response.createException().flatMap(reactor.core.publisher.Mono::error)
                                    : reactor.core.publisher.Mono.just(response)))
                    .baseUrl(address.baseUri())
                    .defaultHeaders(headers -> securityPolicy.safeHeaders(config, secretResolver.resolveMap(config.getHeaders()))
                            .forEach(headers::add));
            WebClientStreamableHttpTransport httpTransport = WebClientStreamableHttpTransport.builder(webClientBuilder)
                    .endpoint(address.endpoint())
                    .jsonMapper(jsonMapper)
                    .openConnectionOnStartup(true)
                    .build();
            return singleAttempt(httpTransport);
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
        return singleAttempt(new StdioClientTransport(parameters, jsonMapper));
    }

    private McpSyncClient singleAttempt(io.modelcontextprotocol.spec.McpClientTransport transport) {
        OpsMcpSingleAttemptTransport bounded = new OpsMcpSingleAttemptTransport(transport, dispatchBudget);
        McpSyncClient client = McpClient.sync(bounded).requestTimeout(Duration.ofSeconds(60))
                .initializationTimeout(Duration.ofSeconds(15)).transportContextProvider(OpsMcpRequestScope::transportContext)
                // Output validation is performed once at our full-envelope callback boundary.
                .enableCallToolSchemaCaching(false).jsonSchemaValidator(jsonSchemaValidator).build();
        transports.put(client, new java.lang.ref.WeakReference<>(bounded));
        return client;
    }

    void onConnectionFailure(McpSyncClient client, Runnable invalidator) {
        var reference = transports.get(client);
        var transport = reference == null ? null : reference.get();
        if (transport != null) transport.onBroken(invalidator);
    }

    public String cacheKey(OpsMcpServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MCP_CLIENT_CONFIG_REQUIRED");
        }
        java.util.Map<String, Object> identity = new java.util.LinkedHashMap<>();
        identity.put("projectId", value(config.getProjectId()));
        identity.put("name", value(config.getName()));
        identity.put("transport", value(config.getTransport()));
        identity.put("command", secretResolver.resolve(config.getCommand()));
        identity.put("url", secretResolver.resolve(config.getUrl()));
        identity.put("args", safeArgs(config));
        identity.put("env", secretResolver.resolveMap(config.getEnv()));
        identity.put("headers", secretResolver.resolveMap(config.getHeaders()));
        identity.put("timeoutSeconds", config.getTimeoutSeconds());
        identity.put("toolCapabilities", config.getToolCapabilities());
        identity.put("allowedTools", config.getAllowedTools());
        identity.put("notificationTools", config.getNotificationTools());
        identity.put("blockedTools", config.getBlockedTools());
        // Different credentials must never collide after redaction. The cache key itself reveals no secret.
        return "mcp-v2:" + cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(identity);
    }

    /** Stable trusted access identity, independent of Run, phase and local tool selection. */
    public String catalogIdentity(OpsMcpServerConfig config) {
        var identity=new java.util.LinkedHashMap<String,Object>();
        identity.put("project",value(config.getProjectId()));
        identity.put("server",StringUtils.hasText(config.getMcpId())?config.getMcpId():config.getName());
        identity.put("transport",securityPolicy.normalizeTransport(config.getTransport()));
        identity.put("command",value(secretResolver.resolve(config.getCommand())));
        identity.put("url",value(secretResolver.resolve(config.getUrl())));
        identity.put("args",safeArgs(config));
        identity.put("env",secretResolver.resolveMap(config.getEnv()));
        identity.put("headers",secretResolver.resolveMap(config.getHeaders()));
        return cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(identity);
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
