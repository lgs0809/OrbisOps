package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptor;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.text;

/** Builds executable MCP runtime configuration from a typed project descriptor. */
@Component
public class OpsProjectMcpRuntimeConfigFactory {

    private final OpsSecretResolver secretResolver;
    private final OpsProjectMcpConfigBuilder configBuilder;

    public OpsProjectMcpRuntimeConfigFactory(OpsSecretResolver secretResolver) {
        if (secretResolver == null) {
            throw new IllegalArgumentException("OPS_SECRET_RESOLVER_REQUIRED");
        }
        OpsProjectMcpTypePolicy typePolicy = new OpsProjectMcpTypePolicy();
        OpsProjectMcpEndpointParser endpointParser =
                new OpsProjectMcpEndpointParser(typePolicy);
        OpsProjectMcpScriptLocator scriptLocator =
                new OpsProjectMcpScriptLocator();
        OpsProjectMcpEnvironmentProjector environmentProjector =
                new OpsProjectMcpEnvironmentProjector(
                        typePolicy,
                        endpointParser);
        this.secretResolver = secretResolver;
        this.configBuilder = new OpsProjectMcpConfigBuilder(
                typePolicy,
                scriptLocator,
                environmentProjector);
    }

    public OpsMcpServerConfig build(ProjectMcpRuntimeDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "PROJECT_MCP_RUNTIME_DESCRIPTOR_REQUIRED");
        }
        return descriptor.external()
                ? buildExternal(descriptor.mcp())
                : buildInternal(descriptor.mcp(), descriptor.resource());
    }

    public OpsMcpServerConfig buildInternal(
            ProjectMcpDefinition mcp,
            ProjectResourceDefinition resource) {
        if (mcp == null || resource == null) {
            throw new IllegalArgumentException("PROJECT_MCP_INTERNAL_DESCRIPTOR_REQUIRED");
        }
        Map<String, Object> source = resourceView(resource);
        return configBuilder.buildInternal(
                mcpView(mcp),
                source,
                resolvedCredential(resource.credential()));
    }

    public OpsMcpServerConfig buildExternal(ProjectMcpDefinition mcp) {
        if (mcp == null) {
            throw new IllegalArgumentException("PROJECT_MCP_DEFINITION_REQUIRED");
        }
        Map<String, Object> transportConfig = new LinkedHashMap<>(mcp.transportConfig());
        String authMode = text(
                transportConfig.get("authMode"),
                "NONE").toUpperCase(Locale.ROOT);
        String credentialRef = text(
                transportConfig.get("credentialRef"),
                "");
        Map<String, String> headers = new LinkedHashMap<>();
        if ("BEARER".equals(authMode)) {
            if (!secretResolver.isReference(credentialRef)) {
                throw new IllegalStateException(
                        "MCP_CREDENTIAL_REFERENCE_INVALID");
            }
            headers.put("Authorization", "Bearer " + credentialRef);
        }
        return configBuilder.buildExternal(mcpView(mcp), headers);
    }

    private Map<String, Object> mcpView(ProjectMcpDefinition mcp) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mcpId", mcp.mcpId());
        view.put("toolId", mcp.mcpId());
        view.put("mcpName", mcp.mcpName());
        view.put("projectId", mcp.projectId());
        view.put("resourceType", mcp.resourceType());
        view.put("transportType", mcp.transportType());
        view.put("transportConfig", mcp.transportConfig());
        view.put("allowedActions", mcp.allowedActions());
        view.put("riskLevel", mcp.riskLevel().name());
        view.put("readOnly", mcp.readOnly());
        view.put("requestTimeout", mcp.requestTimeout());
        return view;
    }

    private Map<String, Object> resourceView(ProjectResourceDefinition resource) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("resourceId", resource.resourceId());
        view.put("projectId", resource.projectId());
        view.put("type", resource.type().value());
        view.put("environment", resource.environment());
        view.put("endpoint", resource.endpoint());
        view.put("permission", resource.permission());
        view.put("credential", resource.credential());
        return view;
    }

    private Map<String, Object> resolvedCredential(
            Map<String, Object> credential) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put(
                "username",
                text(credential.get("username"), ""));
        resolved.put(
                "password",
                secretResolver.resolve(text(
                        credential.get("passwordRef"),
                        "")));
        if (credential.containsKey("apiKeyRef")) {
            resolved.put(
                    "apiKey",
                    secretResolver.resolve(text(
                            credential.get("apiKeyRef"),
                            "")));
        } else if (credential.containsKey("apiKey")) {
            String apiKey = text(credential.get("apiKey"), "");
            resolved.put(
                    "apiKey",
                    secretResolver.isReference(apiKey)
                            ? secretResolver.resolve(apiKey)
                            : apiKey);
        }
        return resolved;
    }
}
