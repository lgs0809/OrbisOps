package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.changepackage.LandingOperationExecutionBinding;
import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Resolves MCP runtime resources and decorates them with the current execution identity. */
public final class OpsRuntimeMcpResolver {
    private static final Logger log = LoggerFactory.getLogger(OpsRuntimeMcpResolver.class);

    private final OpsMcpToolProvider toolProvider;
    private final OpsMcpRuntimeConfigSourceChain configSources;
    private final Supplier<ProjectMcpAuthorizationApplicationService> authorizationSupplier;
    private final OpsMcpRuntimeCatalogReconciler catalogReconciler;
    private final Supplier<LandingOperationJournalApplicationService> landingJournalSupplier;
    public OpsRuntimeMcpResolver(
            OpsMcpToolProvider toolProvider,
            OpsMcpRuntimeConfigSourceChain configSources,
            Supplier<ProjectMcpAuthorizationApplicationService> authorizationSupplier) {
        this(toolProvider, configSources, authorizationSupplier, null, null);
    }
    public OpsRuntimeMcpResolver(
            OpsMcpToolProvider toolProvider,
            OpsMcpRuntimeConfigSourceChain configSources,
            Supplier<ProjectMcpAuthorizationApplicationService> authorizationSupplier,
            OpsMcpRuntimeCatalogReconciler catalogReconciler) {
        this(toolProvider, configSources, authorizationSupplier, catalogReconciler, null);
    }

    public OpsRuntimeMcpResolver(
            OpsMcpToolProvider toolProvider,
            OpsMcpRuntimeConfigSourceChain configSources,
            Supplier<ProjectMcpAuthorizationApplicationService> authorizationSupplier,
            OpsMcpRuntimeCatalogReconciler catalogReconciler,
            Supplier<LandingOperationJournalApplicationService> landingJournalSupplier) {
        if (toolProvider == null) throw new IllegalArgumentException("MCP_TOOL_PROVIDER_REQUIRED");
        if (configSources == null) throw new IllegalArgumentException("MCP_CONFIG_SOURCE_CHAIN_REQUIRED");
        if (authorizationSupplier == null) {
            throw new IllegalArgumentException("PROJECT_MCP_AUTHORIZATION_SUPPLIER_REQUIRED");
        }
        this.toolProvider = toolProvider;
        this.configSources = configSources;
        this.authorizationSupplier = authorizationSupplier;
        this.catalogReconciler = catalogReconciler;
        this.landingJournalSupplier = landingJournalSupplier;
    }

    public void resolve(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        List<OpsMcpServerConfig> servers = new ArrayList<>(context.getMcpServers());
        for (String mcpId : context.getMcpIds()) {
            if (!StringUtils.hasText(mcpId)) continue;
            OpsMcpRuntimeConfigResolution resolution = configSources.resolve(
                    new OpsMcpRuntimeConfigRequest(context.getProjectId(), mcpId));
            recordResolution(context, resolution);
            if (resolution.matched()) {
                servers.add(resolution.config());
            } else {
                warn(context, warning(resolution));
            }
        }
        decorateRuntimeIdentity(context, servers);
        servers = enforceCapabilityProfile(context, servers);
        reconcileRuntimeCatalog(context, servers);
        context.setMcpServers(servers);
        context.setMcpDefinitionReader(toolProvider::currentAuthorizedDefinition);
        context.setMcpDefinitionsReader(toolProvider::currentAuthorizedDefinitions);
        List<ToolCallback> callbacks = toolProvider.buildToolCallbacks(servers);
        context.setTools(callbacks == null ? new ArrayList<>() : new ArrayList<>(callbacks));
    }

    public List<String> enabledProjectMcpIds(String projectId) {
        if (!StringUtils.hasText(projectId)) return List.of();
        ProjectMcpAuthorizationApplicationService authorization = authorizationSupplier.get();
        if (authorization == null) return List.of();
        List<String> enabledIds = authorization.enabledIds(projectId);
        return enabledIds == null ? List.of() : List.copyOf(enabledIds);
    }

    private void reconcileRuntimeCatalog(
            OpsRuntimeResourceContext context,
            List<OpsMcpServerConfig> servers) {
        if (catalogReconciler == null || servers == null || servers.isEmpty()) return;
        for (OpsMcpServerConfig server : servers) {
            if (server == null) continue;
            try {
                catalogReconciler.reconcileIfNeeded(server);
            } catch (RuntimeException error) {
                log.warn("MCP runtime catalog reconcile failed, mcpId={}, reason={}",
                        firstText(server.getMcpId(), server.getName()),
                        firstText(error.getMessage(), error.getClass().getSimpleName()));
                context.record(OpsRuntimeEvent.builder()
                        .eventType("MCP_RUNTIME_CATALOG_RECONCILE_FAILED")
                        .status("WARNING")
                        .summary("MCP 运行目录同步失败：" + firstText(server.getMcpId(), server.getName()))
                        .payload(Map.of(
                                "mcpId", firstText(server.getMcpId(), server.getName()),
                                "reason", firstText(error.getMessage(), error.getClass().getSimpleName())))
                        .build());
            }
        }
    }

    private List<OpsMcpServerConfig> enforceCapabilityProfile(
            OpsRuntimeResourceContext context,
            List<OpsMcpServerConfig> servers) {
        if (servers == null || servers.isEmpty()) return List.of();
        AgentRunExecutionContext executionContext = context.getExecutionContext();
        OpsRuntimeAgentAuthority agentAuthority = OpsRuntimeAgentAuthority.resolve(context);
        String stage = switch (agentAuthority) {
            case OBSERVE_ONLY -> AgentExecutionStage.INVESTIGATE.name();
            case PREPARE_CHANGE -> AgentExecutionStage.PREPARE.name();
            case PROD_FULL -> AgentExecutionStage.LANDING.name();
        };
        CapabilityProfile profile = switch (agentAuthority) {
            case OBSERVE_ONLY -> CapabilityProfile.DATA_READONLY;
            case PREPARE_CHANGE -> CapabilityProfile.TEST_FULL;
            case PROD_FULL -> CapabilityProfile.PROD_FULL;
        };
        List<OpsMcpServerConfig> allowed = new ArrayList<>();
        for (OpsMcpServerConfig server : servers) {
            if (server == null) continue;
            Map<String, String> capabilities = server.getToolCapabilities() == null
                    ? Map.of()
                    : server.getToolCapabilities();
            String configuredStages = firstText(capabilities.get("allowedStages"), "");
            boolean stagesDeclared = StringUtils.hasText(configuredStages);
            boolean stageAllowed = stagesDeclared
                    && java.util.Arrays.stream(configuredStages.split(","))
                    .map(String::trim)
                    .anyMatch(stage::equalsIgnoreCase);
            String environment = firstText(capabilities.get("resourceEnvironment"), "unknown");
            String normalizedEnvironment = environment.toLowerCase(java.util.Locale.ROOT);
            boolean environmentKnown = java.util.Set.of(
                    "prod", "production", "prod-like", "production-like", "shared",
                    "test", "testing", "dev", "development",
                    "staging", "sandbox", "local", "external")
                    .contains(normalizedEnvironment);
            boolean production = java.util.Set.of(
                    "prod", "production", "prod-like", "production-like", "shared")
                    .contains(normalizedEnvironment);
            String readOnlyValue = capabilities.get("readOnly");
            boolean readOnlyDeclared = StringUtils.hasText(readOnlyValue);
            boolean readOnly = readOnlyDeclared && Boolean.parseBoolean(readOnlyValue);
            boolean governanceComplete = stagesDeclared && environmentKnown && readOnlyDeclared;
            boolean proposalStageAllowed = agentAuthority == OpsRuntimeAgentAuthority.PREPARE_CHANGE
                    && production
                    && !readOnly
                    && java.util.Arrays.stream(configuredStages.split(","))
                    .map(String::trim)
                    .anyMatch(AgentExecutionStage.LANDING.name()::equalsIgnoreCase);
            boolean exposureStageAllowed = stageAllowed || proposalStageAllowed;
            boolean profileAllowed = switch (profile) {
                case DATA_READONLY, PROD_DIAGNOSTIC -> governanceComplete && readOnly;
                case TEST_FULL -> governanceComplete && (
                        readOnly
                                || (!production && agentAuthority.mayPrepareChange())
                                || (production
                                && !readOnly
                                && agentAuthority == OpsRuntimeAgentAuthority.PREPARE_CHANGE));
                case PROD_FULL -> governanceComplete
                        && stage.equals(AgentExecutionStage.LANDING.name());
            };
            boolean landingBound = executionContext == null
                    || executionContext.stage() != AgentExecutionStage.LANDING
                    || (stageAllowed && executionContext.approvedPackage().isPresent());
            if (exposureStageAllowed && profileAllowed && landingBound) {
                allowed.add(server);
                continue;
            }
            context.record(OpsRuntimeEvent.builder()
                    .eventType("MCP_CAPABILITY_BLOCKED")
                    .status("BLOCKED")
                    .summary("MCP 资源超出当前 Stage/Profile：" + firstText(server.getMcpId(), server.getName()))
                    .payload(Map.of(
                            "stage", stage,
                            "capabilityProfile", profile.name(),
                            "resourceEnvironment", environment,
                            "readOnly", readOnly,
                            "allowedStages", configuredStages,
                            "mcpId", firstText(server.getMcpId(), server.getName())))
                    .build());
        }
        return List.copyOf(allowed);
    }

    private void recordResolution(
            OpsRuntimeResourceContext context,
            OpsMcpRuntimeConfigResolution resolution) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("owner", context.ownerLabel());
        payload.put("mcpId", resolution.request().mcpId());
        payload.put("sourceId", resolution.selectedSourceId());
        payload.put("outcome", resolution.terminalOutcome().name());
        payload.put("reason", resolution.terminalReason());
        payload.put("fallback", resolution.fallback());
        payload.put("fallbackReason", resolution.fallbackReason());
        payload.put("attempts", resolution.attempts().stream()
                .map(attempt -> Map.<String, Object>of(
                        "sourceId", attempt.sourceId(),
                        "outcome", attempt.outcome().name(),
                        "reason", attempt.reason()))
                .toList());
        context.record(OpsRuntimeEvent.builder()
                .eventType("MCP_CONFIG_RESOLVED")
                .status(resolution.matched() ? "SUCCEEDED" : "SKIPPED")
                .summary(resolution.matched()
                        ? "MCP Runtime Config 已解析：" + resolution.request().mcpId()
                                + " source=" + resolution.selectedSourceId()
                        : warning(resolution))
                .payload(Map.copyOf(payload))
                .build());
    }

    private String warning(OpsMcpRuntimeConfigResolution resolution) {
        if (resolution.blocked()) {
            return "MCP 配置被阻断：" + resolution.request().mcpId()
                    + " source=" + resolution.selectedSourceId()
                    + " reason=" + resolution.terminalReason();
        }
        return "MCP 配置不存在或未启用：" + resolution.request().mcpId()
                + " reason=" + resolution.terminalReason();
    }

    private void decorateRuntimeIdentity(
            OpsRuntimeResourceContext context,
            List<OpsMcpServerConfig> servers) {
        String runId = runtimeRunId(context.getRequest());
        String agentId = context.getDefinition() == null
                ? ""
                : firstText(context.getDefinition().getAgentId(), "");
        String nodeId = context.getNode() == null
                ? agentScopeId(context.getAgentScope())
                : firstText(context.getNode().getNodeId(), "");
        AgentRunExecutionContext executionContext = context.getExecutionContext();
        String stage = toolCallStage(context);
        OpsRuntimeAgentAuthority agentAuthority = OpsRuntimeAgentAuthority.resolve(context);
        List<LandingOperationExecutionBinding> landingBindings = landingBindings(context, executionContext);
        for (OpsMcpServerConfig server : servers) {
            if (server == null) continue;
            if (!StringUtils.hasText(server.getProjectId())) {
                server.setProjectId(context.getProjectId());
            }
            server.setRunId(runId);
            server.setAgentId(agentId);
            server.setNodeId(nodeId);
            server.setWorkSessionClaim(workSessionClaim(context.getRequest()));
            server.setToolCallStage(stage);
            server.setRuntimeAuthority(agentAuthority.name());
            server.setAuthorityDeadline(executionContext == null ? null : executionContext.deadline());
            server.setLandingOperationBindings(landingBindings);
            if (executionContext != null
                    && executionContext.stage() == AgentExecutionStage.LANDING) {
                var approved = executionContext.approvedPackage().orElseThrow();
                server.setLandingApproved(true);
                server.setChangePackageId(approved.packageId());
                server.setApprovedPackageHash(approved.packageHash());
                server.setApprovedPackageVersion(Math.toIntExact(approved.packageVersion()));
                server.setInternalCaller("UNIFIED_AGENT_RUNTIME");
            } else {
                server.setLandingApproved(false);
            }
        }
    }

    private List<LandingOperationExecutionBinding> landingBindings(
            OpsRuntimeResourceContext context,
            AgentRunExecutionContext executionContext) {
        if (executionContext == null || executionContext.stage() != AgentExecutionStage.LANDING) {
            return List.of();
        }
        String landingRunId = runtimeRunId(context.getRequest());
        if (!StringUtils.hasText(landingRunId) || landingJournalSupplier == null) return List.of();
        LandingOperationJournalApplicationService journal = landingJournalSupplier.get();
        if (journal == null) {
            throw new IllegalStateException("LANDING_OPERATION_JOURNAL_SERVICE_UNAVAILABLE");
        }
        try {
            List<LandingOperationExecutionBinding> bindings = journal.operationExecutionBindings(landingRunId);
            return bindings == null ? List.of() : List.copyOf(bindings);
        } catch (RuntimeException error) {
            throw new IllegalStateException(
                    "LANDING_OPERATION_BINDINGS_LOAD_FAILED：run=" + landingRunId, error);
        }
    }

    private Map<String, Object> workSessionClaim(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return Map.of();
        Map<String, Object> source = request.getMetadata();
        Map<String, Object> claim = new LinkedHashMap<>();
        copyClaim(source, claim, OpsWorkSessionClaimMetadata.ATTEMPT_ID);
        copyClaim(source, claim, OpsWorkSessionClaimMetadata.LEASE_TOKEN);
        copyClaim(source, claim, OpsWorkSessionClaimMetadata.FENCING_TOKEN);
        copyClaim(source, claim, OpsWorkSessionClaimMetadata.STATE_VERSION);
        copyClaim(source, claim, OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH);
        return claim.isEmpty() ? Map.of() : Map.copyOf(claim);
    }

    private void copyClaim(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) target.put(key, value);
    }

    private String agentScopeId(OpsAgentScopeConfig scope) {
        return scope == null ? "" : firstText(scope.getAgentId(), scope.getName());
    }

    private String toolCallStage(OpsRuntimeResourceContext context) {
        AgentRunExecutionContext executionContext = context.getExecutionContext();
        if (executionContext == null) {
            // Legacy/direct callers fail closed. Agent Definition phase/capabilities are never authority.
            return OpsToolCallStage.INVESTIGATE.name();
        }
        if (executionContext.expired(java.time.Instant.now())) {
            throw new SecurityException("AGENT_RUN_EXECUTION_CONTEXT_EXPIRED");
        }
        OpsRuntimeAgentAuthority authority = OpsRuntimeAgentAuthority.resolve(context);
        return switch (authority) {
            case OBSERVE_ONLY -> OpsToolCallStage.INVESTIGATE.name();
            case PREPARE_CHANGE -> OpsToolCallStage.PREPARE.name();
            case PROD_FULL -> OpsToolCallStage.LANDING.name();
        };
    }

    private String runtimeRunId(OpsAgentChatRequest request) {
        return OpsRuntimeToolContributionSupport.runtimeRunId(request);
    }

    private void warn(OpsRuntimeResourceContext context, String summary) {
        context.record(OpsRuntimeEvent.builder()
                .eventType("RESOURCE_WARN")
                .status("SUCCEEDED")
                .summary(summary)
                .payload(Map.of("owner", context.ownerLabel()))
                .build());
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

}
