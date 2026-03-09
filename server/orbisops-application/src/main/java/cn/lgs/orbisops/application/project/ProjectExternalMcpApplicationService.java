package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProjectExternalMcpApplicationService {

    private static final Set<String> TRANSPORT_TYPES =
            Set.of("sse", "streamable-http");
    private static final Set<String> AUTH_MODES = Set.of("NONE", "BEARER");

    private final ProjectDefinitionApplicationService definitionService;
    private final ProjectMcpCatalogApplicationService catalogService;
    private final ProjectExternalMcpCredentialReferencePort credentialReferencePort;
    private final ProjectWorkspaceProjectionPort workspacePort;

    public ProjectExternalMcpApplicationService(
            ProjectDefinitionApplicationService definitionService,
            ProjectMcpCatalogApplicationService catalogService,
            ProjectExternalMcpCredentialReferencePort credentialReferencePort,
            ProjectWorkspaceProjectionPort workspacePort) {
        if (definitionService == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        }
        if (catalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        if (credentialReferencePort == null) {
            throw new IllegalArgumentException("PROJECT_EXTERNAL_MCP_CREDENTIAL_PORT_REQUIRED");
        }
        if (workspacePort == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECTION_PORT_REQUIRED");
        }
        this.definitionService = definitionService;
        this.catalogService = catalogService;
        this.credentialReferencePort = credentialReferencePort;
        this.workspacePort = workspacePort;
    }

    public Map<String, Object> register(
            String projectId,
            String mcpId,
            String name,
            String endpoint,
            String transportType,
            String credentialRef,
            String authMode) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        if (!definitionService.exists(id)) {
            throw new IllegalArgumentException("项目不存在：" + id);
        }
        String normalizedMcpId = normalizeId(text(mcpId, name));
        if (normalizedMcpId.isBlank()) {
            throw new IllegalArgumentException("MCP 名称不能为空");
        }
        String normalizedEndpoint = required(endpoint, "PROJECT_EXTERNAL_MCP_ENDPOINT_REQUIRED");
        String transport = text(transportType, "streamable-http")
                .toLowerCase(Locale.ROOT);
        if (!TRANSPORT_TYPES.contains(transport)) {
            throw new IllegalArgumentException(
                    "自然语言导入仅支持 sse 或 streamable-http");
        }
        String reference = value(credentialRef);
        String normalizedAuthMode = text(
                authMode,
                reference.isBlank() ? "NONE" : "BEARER")
                .toUpperCase(Locale.ROOT);
        if (!AUTH_MODES.contains(normalizedAuthMode)) {
            throw new IllegalArgumentException("当前仅支持 NONE 或 BEARER 鉴权");
        }
        if ("BEARER".equals(normalizedAuthMode)
                && !credentialReferencePort.isReference(reference)) {
            throw new IllegalArgumentException(
                    "MCP 凭据必须使用 ${env:NAME} 引用，不能保存明文 token");
        }

        Map<String, Object> transportConfig = new LinkedHashMap<>();
        transportConfig.put("endpoint", normalizedEndpoint);
        transportConfig.put("authMode", normalizedAuthMode);
        transportConfig.put("credentialRef", reference);
        transportConfig.put("registeredBy", "CAPABILITY_IMPORT_SERVICE");
        transportConfig.put("connectionStatus", "REGISTERED");
        transportConfig.put("policyStatus", "PENDING_REVIEW");
        transportConfig.put("discoveryError", "");

        LocalDateTime now = LocalDateTime.now();
        ProjectMcpDefinition definition = new ProjectMcpDefinition(
                normalizedMcpId,
                text(name, normalizedMcpId),
                id,
                "",
                "external_mcp",
                transport,
                "",
                transportConfig,
                List.of(),
                ProjectMcpRiskLevel.HIGH,
                false,
                Map.of(
                        "source", "PLATFORM_TOOL_POLICY",
                        "reviewRequired", true),
                30,
                ProjectMcpStatus.PENDING_REVIEW,
                now,
                now);
        return saveAndMaterialize(definition);
    }

    public Map<String, Object> recordDiscovery(
            String projectId,
            String mcpId,
            List<Map<String, Object>> remoteTools,
            String connectionStatus,
            String discoveryError) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        String toolId = required(mcpId, "PROJECT_MCP_ID_REQUIRED");
        ProjectMcpDefinition current = catalogService.find(id, toolId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "项目 MCP 不存在：" + toolId));
        if (!"external_mcp".equals(current.resourceType())) {
            throw new IllegalArgumentException(
                    "仅外部 MCP 支持发现状态写回：" + toolId);
        }
        Map<String, Object> transportConfig =
                new LinkedHashMap<>(current.transportConfig());
        transportConfig.put("remoteTools", copyTools(remoteTools));
        transportConfig.put("connectionStatus",
                text(connectionStatus, "DISCOVERED"));
        transportConfig.put("policyStatus", "PENDING_REVIEW");
        transportConfig.put("discoveryError", value(discoveryError));

        ProjectMcpDefinition definition = current.withDiscoveryState(
                transportConfig,
                ProjectMcpStatus.PENDING_REVIEW,
                LocalDateTime.now());
        return saveAndMaterialize(definition);
    }

    private Map<String, Object> saveAndMaterialize(ProjectMcpDefinition definition) {
        ProjectMcpDefinition saved = catalogService.save(definition);
        workspacePort.materializeMcp(saved);
        return catalogService.view(saved);
    }

    private List<Map<String, Object>> copyTools(
            List<Map<String, Object>> remoteTools) {
        if (remoteTools == null || remoteTools.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> tool : remoteTools) {
            result.add(tool == null
                    ? Map.of()
                    : new LinkedHashMap<>(tool));
        }
        return List.copyOf(result);
    }

    private String normalizeId(String input) {
        String normalized = value(input).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "-");
        normalized = normalized.replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized;
    }

    private String required(Object input, String error) {
        String normalized = input == null ? "" : String.valueOf(input).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object input, String fallback) {
        String normalized = input == null ? "" : String.valueOf(input).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
