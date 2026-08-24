package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpRoutingDecision;
import cn.lgs.orbisops.domain.mcp.model.McpSchemaCacheEntry;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicySuggestion;
import cn.lgs.orbisops.domain.mcp.service.McpToolPolicyGovernance;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class McpDiscoveryApplicationService implements McpDiscoveryPort {

    private final McpProjectDirectoryPort projects;
    private final McpProjectToolCatalogPort catalog;
    private final SelectRuntimeMcpToolsQuery runtimeTools;
    private final IMcpRuntimeCatalogRepository runtimeCatalog;
    private final McpToolSnapshotStorePort snapshots;
    private final IMcpToolPolicyRepository policies;
    private final McpRuntimeAuditPort audit;
    private final McpPolicySuggestionPort suggestions;
    private final McpDiscoveryModelMapper mapper;
    private final McpPolicyCommandModelMapper policyMapper;
    private final McpToolPolicyGovernance governance = new McpToolPolicyGovernance();
    private final Clock clock;

    public McpDiscoveryApplicationService(McpProjectDirectoryPort projects,
                                          McpProjectToolCatalogPort catalog,
                                          SelectRuntimeMcpToolsQuery runtimeTools,
                                          IMcpRuntimeCatalogRepository runtimeCatalog,
                                          McpToolSnapshotStorePort snapshots,
                                          IMcpToolPolicyRepository policies,
                                          McpJsonCodec jsonCodec,
                                          McpRuntimeAuditPort audit,
                                          McpPolicySuggestionPort suggestions) {
        this(projects, catalog, runtimeTools, runtimeCatalog, snapshots, policies,
                jsonCodec, audit, suggestions, Clock.systemDefaultZone());
    }

    McpDiscoveryApplicationService(McpProjectDirectoryPort projects,
                                   McpProjectToolCatalogPort catalog,
                                   SelectRuntimeMcpToolsQuery runtimeTools,
                                   IMcpRuntimeCatalogRepository runtimeCatalog,
                                   McpToolSnapshotStorePort snapshots,
                                   IMcpToolPolicyRepository policies,
                                   McpJsonCodec jsonCodec,
                                   McpRuntimeAuditPort audit,
                                   McpPolicySuggestionPort suggestions,
                                   Clock clock) {
        if (projects == null) throw new IllegalArgumentException("MCP_PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (catalog == null) throw new IllegalArgumentException("PROJECT_MCP_CATALOG_PORT_REQUIRED");
        if (runtimeTools == null) throw new IllegalArgumentException("MCP_RUNTIME_TOOLS_QUERY_REQUIRED");
        if (runtimeCatalog == null) throw new IllegalArgumentException("MCP_RUNTIME_CATALOG_REPOSITORY_REQUIRED");
        if (snapshots == null) throw new IllegalArgumentException("MCP_TOOL_SNAPSHOT_PORT_REQUIRED");
        if (policies == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_REPOSITORY_REQUIRED");
        if (jsonCodec == null) throw new IllegalArgumentException("MCP_JSON_CODEC_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("MCP_RUNTIME_AUDIT_PORT_REQUIRED");
        if (suggestions == null) throw new IllegalArgumentException("MCP_POLICY_SUGGESTION_PORT_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("MCP_DISCOVERY_CLOCK_REQUIRED");
        this.projects = projects;
        this.catalog = catalog;
        this.runtimeTools = runtimeTools;
        this.runtimeCatalog = runtimeCatalog;
        this.snapshots = snapshots;
        this.policies = policies;
        this.audit = audit;
        this.suggestions = suggestions;
        this.mapper = new McpDiscoveryModelMapper(jsonCodec);
        this.policyMapper = new McpPolicyCommandModelMapper(jsonCodec, clock);
        this.clock = clock;
    }

    @Override
    public McpDiscoverySelectionResult select(McpDiscoverySelectionRequest command) {
        McpDiscoverySelectionRequest required = required(command, "MCP_DISCOVERY_COMMAND_REQUIRED");
        String projectId = project(required.projectId());
        McpDiscoveryScoringPolicy scoring = new McpDiscoveryScoringPolicy();

        List<McpDiscoveryToolCandidate> authorized = catalog.list(projectId).stream()
                .filter(tool -> tool.status() == ProjectMcpStatus.ENABLED)
                .filter(tool -> required.explicitToolId().isBlank()
                        || required.explicitToolId().equals(tool.mcpId()))
                .filter(tool -> !runtimeTools.runtimeExecutableTools(
                        projectId, tool.mcpId(), List.of(), List.of(),
                        required.stage().isBlank() ? "PREPARE" : required.stage()).isEmpty())
                .map(tool -> McpDiscoveryToolCandidate.from(tool, scoring.score(tool, required)))
                .sorted(Comparator.comparingDouble(McpDiscoveryToolCandidate::score).reversed())
                .limit(required.limit())
                .toList();

        String decisionId = "tool-route-" + UUID.randomUUID();
        McpDiscoverySelectionResult result = new McpDiscoverySelectionResult(
                decisionId,
                projectId,
                required.agentId(),
                required.nodeId(),
                required.runId(),
                required.capability(),
                authorized,
                authorized.isEmpty()
                        ? "当前项目没有匹配授权工具"
                        : "按项目授权、只读优先、风险等级和能力关键词排序");
        Map<String, Object> decision = result.view(required.auditPayload());
        runtimeStore().saveRoutingDecision(new McpRoutingDecision(
                decisionId, projectId, required.agentId(), required.nodeId(), required.runId(),
                required.capability(), mapper.encode(required.auditPayload()),
                mapper.encode(result.selectedToolViews()), result.reason(), result.status(), null));
        audit.recordRuntimeEvent(projectId, required.agentId(), required.userId(),
                "tool-router", "select", decisionId, "LOW",
                result.selected() ? "SUCCEEDED" : "SKIPPED", decision);
        return result;
    }

    @Override
    public McpHydratedToolSchema hydrateSchema(McpSchemaHydrationRequest command) {
        McpSchemaHydrationRequest required = required(command, "MCP_SCHEMA_HYDRATION_COMMAND_REQUIRED");
        String projectId = project(required.projectId());
        McpProjectToolDescriptor tool = catalog.find(projectId, required.toolId())
                .orElseThrow(() -> new IllegalArgumentException("项目未授权工具：" + required.toolId()));
        McpRemoteToolDescriptor remote = tool.remoteTool(required.remoteToolName());
        McpAuthoritativeToolDefinition definition = authoritativeDefinition(required, tool);

        if (required.remoteToolRequested() && !definition.hydrated()) {
            throw new SecurityException("MCP_TOOL_SCHEMA_NOT_HYDRATED：必须先从真实 MCP Server 获取工具定义");
        }

        boolean metadataComplete = required.remoteToolRequested() ? remote.metadataComplete() : true;
        List<String> allowedActions = metadataComplete
                ? (required.remoteToolRequested() ? remote.allowedActions() : tool.allowedActions())
                : List.of("UNKNOWN_MUTATING");
        McpRiskLevel riskLevel = metadataComplete
                ? (required.remoteToolRequested() ? remote.riskLevel() : tool.riskLevel())
                : McpRiskLevel.HIGH;
        boolean readOnly = metadataComplete
                && (required.remoteToolRequested() ? remote.readOnly() : tool.readOnly());
        String description = definition.hydrated()
                ? definition.description()
                : remote.description();
        String schemaSource = definition.hydrated()
                ? text(definition.schemaSource(), "REMOTE_MCP_TOOL_DEFINITION")
                : "";
        String effectiveToolName = required.remoteToolRequested()
                ? required.remoteToolName()
                : tool.toolName();

        Map<String, Object> hashSchema = new LinkedHashMap<>();
        hashSchema.put("resourceType", tool.resourceType());
        hashSchema.put("transportType", tool.transportType());
        hashSchema.put("description", description);
        if (definition.hydrated()) hashSchema.put("schema", definition.inputSchema());
        if (definition.outputSchema() != null) hashSchema.put("outputSchema", definition.outputSchema());
        String schemaHash = mapper.schemaHash(
                projectId, tool.mcpId(), tool.mcpId(), effectiveToolName,
                hashSchema, remote.rawMetadata());

        McpHydratedToolSchema base = new McpHydratedToolSchema(
                projectId,
                tool.mcpId(),
                tool.mcpId(),
                tool.toolName(),
                required.remoteToolName(),
                tool.resourceType(),
                tool.transportType(),
                tool.requestTimeout(),
                allowedActions,
                riskLevel,
                readOnly,
                metadataComplete,
                tool.permissionPolicy(),
                definition.hydrated(),
                definition.inputSchema(),
                description,
                schemaSource,
                schemaHash,
                McpToolPolicyProjection.missing(), definition.outputSchema());

        snapshotStore().save(new McpToolSchemaSnapshot(
                "mcp-snapshot-" + UUID.randomUUID(),
                projectId,
                tool.mcpId(),
                tool.mcpId(),
                effectiveToolName,
                schemaHash,
                base,
                remote.rawMetadata(),
                metadataComplete));

        McpToolPolicyProjection policy = resolvePolicy(base, remote.rawMetadata());
        McpHydratedToolSchema hydrated = base.withPolicy(policy);
        String cacheId = schemaCacheId(projectId, hydrated);
        runtimeStore().saveSchemaCache(new McpSchemaCacheEntry(
                cacheId, projectId, hydrated.toolId(), mapper.encode(hydrated.view()), "ACTIVE"));
        Map<String, Object> auditPayload = Map.of(
                "toolId", hydrated.toolId(),
                "toolName", hydrated.toolName(),
                "remoteToolName", hydrated.remoteToolName(),
                "metadataComplete", hydrated.metadataComplete());
        audit.recordRuntimeEvent(projectId, required.agentId(), required.userId(),
                "tool-router", "hydrate-schema", cacheId,
                hydrated.policy().riskLevel().name(), "SUCCEEDED", auditPayload);
        return hydrated;
    }

    /**
     * Keep the human-readable cache key for ordinary tool ids, but fall back to
     * a deterministic bounded key when a project/MCP/tool combination would
     * overflow the VARCHAR(160) storage column.  Hydration must not fail just
     * because a valid remote tool has a long name or belongs to a long MCP id.
     */
    private String schemaCacheId(String projectId, McpHydratedToolSchema hydrated) {
        String readable = "tool-schema-" + projectId + "-" + hydrated.toolId() + "-"
                + hydrated.effectiveToolName() + "-" + hydrated.schemaHash();
        if (readable.length() <= 160) return readable;
        return "tool-schema-" + CanonicalObjectHasher.sha256Text(readable);
    }

    private McpAuthoritativeToolDefinition authoritativeDefinition(
            McpSchemaHydrationRequest request,
            McpProjectToolDescriptor tool) {
        if (request.authoritativeDefinition().hydrated()) {
            McpAuthoritativeToolDefinition supplied = request.authoritativeDefinition();
            return new McpAuthoritativeToolDefinition(
                    supplied.description(), supplied.inputSchema(), supplied.outputSchema(),
                    text(supplied.schemaSource(), "REMOTE_MCP_TOOL_DEFINITION"));
        }
        if (!request.remoteToolRequested()) return request.authoritativeDefinition();
        return snapshots.latestHydratedDefinition(
                        request.projectId(), tool.mcpId(), request.remoteToolName(), 20)
                .orElse(McpAuthoritativeToolDefinition.empty());
    }

    private McpToolPolicyProjection resolvePolicy(McpHydratedToolSchema schema,
                                                  Map<String, Object> remoteMetadata) {
        markStalePolicies(schema.projectId(), schema.mcpId(),
                schema.effectiveToolName(), schema.schemaHash());

        Optional<McpToolPolicy> reviewed = policies.available()
                ? policies.findActiveReviewed(schema.projectId(), schema.mcpId(),
                schema.effectiveToolName(), schema.schemaHash())
                : Optional.empty();
        if (reviewed.isPresent()) return policyMapper.projection(reviewed.get());

        if (trustedPlatformReadOnly(schema, remoteMetadata)) {
            McpToolPolicy verified = platformVerifiedPolicy(schema, remoteMetadata);
            policyStore().save(verified);
            audit.recordRuntimeEvent(verified.projectId(), "", "",
                    "mcp-tool-policy", "system-verified", verified.policyId(),
                    verified.riskLevel().name(), "ACTIVE", policyMapper.view(verified));
            Optional<McpToolPolicy> persisted = policies.findActiveReviewed(
                    schema.projectId(), schema.mcpId(), schema.effectiveToolName(), schema.schemaHash());
            return policyMapper.projection(persisted.orElse(verified));
        }

        Optional<McpToolPolicy> pending = policies.available()
                ? policies.findPendingSuggestion(schema.projectId(), schema.mcpId(),
                schema.effectiveToolName(), schema.schemaHash())
                : Optional.empty();
        if (pending.isPresent()) {
            staleDuplicateSuggestions(pending.get());
            return policyMapper.projection(pending.get());
        }

        McpToolPolicy suggested = suggestedPolicy(schema, remoteMetadata);
        boolean inserted = persistSuggestedPolicy(suggested);
        Optional<McpToolPolicy> reviewedAfterInsert = policies.findActiveReviewed(
                schema.projectId(), schema.mcpId(), schema.effectiveToolName(), schema.schemaHash());
        if (reviewedAfterInsert.isPresent()) return policyMapper.projection(reviewedAfterInsert.get());

        Optional<McpToolPolicy> pendingAfterInsert = policies.findPendingSuggestion(
                schema.projectId(), schema.mcpId(), schema.effectiveToolName(), schema.schemaHash());
        if (pendingAfterInsert.isPresent()) {
            staleDuplicateSuggestions(pendingAfterInsert.get());
            return policyMapper.projection(pendingAfterInsert.get());
        }

        Optional<McpToolPolicy> exact = policies.findById(schema.projectId(), suggested.policyId());
        if (exact.isPresent()) return policyMapper.projection(exact.get());
        if (!inserted) {
            throw new IllegalStateException("MCP_TOOL_POLICY_SUGGESTION_CONFLICT");
        }
        staleDuplicateSuggestions(suggested);
        return policyMapper.projection(suggested);
    }

    private boolean trustedPlatformReadOnly(McpHydratedToolSchema schema,
                                            Map<String, Object> remoteMetadata) {
        Map<String, Object> metadata = remoteMetadata == null ? Map.of() : remoteMetadata;
        if (!Boolean.TRUE.equals(metadata.get("platformGenerated"))
                || !schema.metadataComplete()
                || !schema.schemaHydrated()
                || !schema.readOnly()
                || !(schema.riskLevel() == McpRiskLevel.LOW
                || schema.riskLevel() == McpRiskLevel.MEDIUM)
                || schema.allowedActions().isEmpty()) {
            return false;
        }
        return schema.allowedActions().stream().allMatch(this::readAction);
    }

    private boolean readAction(String action) {
        String normalized = text(action, "").toUpperCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) return false;
        return normalized.contains("READ")
                || normalized.contains("GET")
                || normalized.contains("LIST")
                || normalized.contains("QUERY")
                || normalized.contains("SEARCH")
                || normalized.contains("SELECT")
                || normalized.contains("SHOW")
                || normalized.contains("EXPLAIN")
                || normalized.contains("INSPECT")
                || normalized.contains("DISCOVER");
    }

    private McpToolPolicy platformVerifiedPolicy(McpHydratedToolSchema schema,
                                                 Map<String, Object> remoteMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "PLATFORM_GENERATED_READONLY_METADATA");
        metadata.put("metadataComplete", true);
        metadata.put("platformGenerated", true);
        metadata.put("remoteMetadata", remoteMetadata == null ? Map.of() : remoteMetadata);
        metadata.put("disclosureTier", schema.riskLevel() == McpRiskLevel.LOW ? "CORE" : "EXTENSION");
        LocalDateTime now = LocalDateTime.now(clock);
        return new McpToolPolicy(
                0L,
                deterministicPolicyId(schema),
                schema.projectId(),
                schema.mcpId(),
                schema.toolId(),
                schema.effectiveToolName(),
                schema.schemaHash(),
                "READ_EXTERNAL_STATE",
                "TARGET_RESOURCE_READ",
                "READ_ONLY",
                text(remoteMetadata == null ? "" : String.valueOf(remoteMetadata.getOrDefault("capability", "")),
                        "READ_ONLY"),
                mapper.encode(schema.allowedActions()),
                schema.riskLevel(),
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                mapper.encode(suggestedArgumentPolicy(schema, true)),
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.SYSTEM_VERIFIED,
                "platform-generated-mcp-policy",
                now,
                "PLATFORM_GENERATED_READONLY_METADATA",
                now,
                mapper.encode(metadata),
                null,
                null);
    }

    private McpToolPolicy suggestedPolicy(McpHydratedToolSchema schema,
                                          Map<String, Object> remoteMetadata) {
        Optional<McpPolicySuggestion> llm = suggestions.suggest(
                new McpPolicySuggestionPort.SuggestionRequest(
                        schema.projectId(), schema.mcpId(), schema.toolId(),
                        schema.effectiveToolName(), schema.schemaHash(),
                        schema.metadataComplete(), schema.view()));
        if (llm.isPresent()) {
            McpPolicySuggestion suggestion = llm.get();
            Map<String, Object> metadata = Map.of(
                    "source", "LLM_POLICY_SUGGESTION",
                    "metadataComplete", schema.metadataComplete(),
                    "rawSuggestion", suggestion.rawSuggestion());
            return policy(
                    schema, suggestion.effectType(), suggestion.effectScope(),
                    suggestion.mutability(), suggestion.capability(),
                    suggestion.allowedActions(), suggestion.riskLevel(), suggestion.readOnly(),
                    suggestion.investigateAllowed(), suggestion.prepareAllowed(), suggestion.landAllowed(),
                    suggestion.requiresApprovedPackage(), suggestion.requiresHumanApproval(),
                    suggestion.requiresDryRun(), suggestion.requiresRollbackPlan(),
                    suggestion.argumentPolicy(), McpToolPolicyReviewStatus.AI_SUGGESTED,
                    "LLM_POLICY_SUGGESTION", metadata);
        }

        McpToolPolicySuggestion fallback = governance.fallbackSuggestion(
                schema.readOnly(), schema.riskLevel(), schema.allowedActions(), schema.metadataComplete());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", fallback.source());
        metadata.put("metadataComplete", schema.metadataComplete());
        metadata.put("remoteMetadata", remoteMetadata == null ? Map.of() : remoteMetadata);
        return policy(
                schema, fallback.effectType(), fallback.effectScope(), fallback.mutability(),
                fallback.capability(), fallback.allowedActions(), fallback.riskLevel(), fallback.readOnly(),
                fallback.investigateAllowed(), fallback.prepareAllowed(), fallback.landAllowed(),
                fallback.requiresApprovedPackage(), fallback.requiresHumanApproval(),
                fallback.requiresDryRun(), fallback.requiresRollbackPlan(),
                suggestedArgumentPolicy(schema, fallback.readOnly()), fallback.reviewStatus(),
                fallback.source(), metadata);
    }

    private McpToolPolicy policy(McpHydratedToolSchema schema,
                                 String effectType,
                                 String effectScope,
                                 String mutability,
                                 String capability,
                                 List<String> allowedActions,
                                 McpRiskLevel riskLevel,
                                 boolean readOnly,
                                 boolean investigateAllowed,
                                 boolean prepareAllowed,
                                 boolean landAllowed,
                                 boolean requiresApprovedPackage,
                                 boolean requiresHumanApproval,
                                 boolean requiresDryRun,
                                 boolean requiresRollbackPlan,
                                 Map<String, Object> argumentPolicy,
                                 McpToolPolicyReviewStatus reviewStatus,
                                 String suggestedBy,
                                 Map<String, Object> metadata) {
        return new McpToolPolicy(
                0L,
                deterministicPolicyId(schema),
                schema.projectId(),
                schema.mcpId(),
                schema.toolId(),
                schema.effectiveToolName(),
                schema.schemaHash(),
                effectType,
                effectScope,
                mutability,
                capability,
                mapper.encode(allowedActions),
                riskLevel,
                readOnly,
                investigateAllowed,
                prepareAllowed,
                landAllowed,
                requiresApprovedPackage,
                requiresHumanApproval,
                requiresDryRun,
                requiresRollbackPlan,
                mapper.encode(argumentPolicy),
                McpToolPolicyStatus.PENDING_REVIEW,
                reviewStatus,
                "",
                null,
                suggestedBy,
                LocalDateTime.now(clock),
                mapper.encode(metadata),
                null,
                null);
    }

    private boolean persistSuggestedPolicy(McpToolPolicy policy) {
        boolean inserted = policyStore().saveSuggestionIfAbsent(policy);
        if (!inserted) return false;
        Map<String, Object> view = policyMapper.view(policy);
        audit.recordRuntimeEvent(policy.projectId(), "", "",
                "mcp-tool-policy", "suggested", policy.policyId(),
                policy.riskLevel().name(), "PENDING_REVIEW", view);
        return true;
    }

    private void markStalePolicies(String projectId,
                                   String mcpId,
                                   String toolName,
                                   String schemaHash) {
        if (schemaHash.isBlank()) return;
        int updated = policyStore().markActivePoliciesStale(projectId, mcpId, toolName, schemaHash);
        if (updated > 0) {
            audit.recordRuntimeEvent(projectId, "", "", "mcp-tool-policy", "stale",
                    mcpId + ":" + toolName, "MEDIUM", "STALE",
                    Map.of("toolName", toolName, "mcpId", mcpId,
                            "schemaHash", schemaHash, "updated", updated));
        }
    }

    private void staleDuplicateSuggestions(McpToolPolicy canonical) {
        int updated = policyStore().markDuplicatePendingSuggestionsStale(
                canonical.projectId(), canonical.mcpId(), canonical.toolName(),
                canonical.schemaHash(), canonical.policyId());
        if (updated > 0) {
            audit.recordRuntimeEvent(canonical.projectId(), "", "", "mcp-tool-policy", "deduplicated",
                    canonical.policyId(), "MEDIUM", "STALE",
                    Map.of("toolName", canonical.toolName(), "mcpId", canonical.mcpId(),
                            "schemaHash", canonical.schemaHash(), "canonicalPolicyId", canonical.policyId(),
                            "updated", updated));
        }
    }

    private String deterministicPolicyId(McpHydratedToolSchema schema) {
        String identity = String.join("\n", schema.projectId(), schema.mcpId(),
                schema.effectiveToolName(), schema.schemaHash());
        return "mcp-policy-" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> suggestedArgumentPolicy(McpHydratedToolSchema schema,
                                                        boolean readOnly) {
        Map<String, Object> policy = new LinkedHashMap<>();
        if (readOnly) {
            policy.put("sqlReadOnlyOnly", true);
            policy.put("forbiddenSqlKeywords", List.of("DROP", "TRUNCATE", "DELETE", "UPDATE", "INSERT",
                    "ALTER", "CREATE", "MERGE", "CALL", "GRANT", "REVOKE"));
            policy.put("maxLimit", 500);
        } else {
            policy.put("requiredKeys", List.of());
            policy.put("forbiddenKeys", List.of("password", "secret", "token", "apiKey", "authorization"));
            policy.put("sqlReadOnlyOnly", false);
        }
        policy.put("source", "SYSTEM_SUGGESTED");
        policy.put("schemaHash", schema.schemaHash());
        return Map.copyOf(policy);
    }

    private String project(String value) {
        String normalized = text(value, "");
        if (normalized.isBlank() || !projects.exists(normalized)) {
            throw new IllegalArgumentException("项目不存在：" + value);
        }
        return normalized;
    }

    private IMcpRuntimeCatalogRepository runtimeStore() {
        if (!runtimeCatalog.available()) {
            throw new IllegalStateException("MCP_RUNTIME_CATALOG_STORE_UNAVAILABLE");
        }
        return runtimeCatalog;
    }

    private McpToolSnapshotStorePort snapshotStore() {
        if (!snapshots.available()) {
            throw new IllegalStateException("MCP_TOOL_SNAPSHOT_STORE_UNAVAILABLE");
        }
        return snapshots;
    }

    private IMcpToolPolicyRepository policyStore() {
        if (!policies.available()) {
            throw new IllegalStateException("MCP_TOOL_POLICY_STORE_UNAVAILABLE");
        }
        return policies;
    }

    private <T> T required(T value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value;
    }

    private String text(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
