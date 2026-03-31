package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.DiscoverMcpToolsProcessManager;
import cn.lgs.orbisops.application.mcp.ManageMcpTemplateUseCase;
import cn.lgs.orbisops.application.mcp.McpAuditPort;
import cn.lgs.orbisops.application.mcp.McpDiscoveryApplicationService;
import cn.lgs.orbisops.application.mcp.McpDiscoveryPort;
import cn.lgs.orbisops.application.mcp.McpGovernanceApplicationService;
import cn.lgs.orbisops.application.mcp.McpJsonCodec;
import cn.lgs.orbisops.application.mcp.McpPolicyCommandApplicationService;
import cn.lgs.orbisops.application.mcp.McpPolicyCommandPort;
import cn.lgs.orbisops.application.mcp.McpPolicySuggestionPort;
import cn.lgs.orbisops.application.mcp.McpProjectDirectoryPort;
import cn.lgs.orbisops.application.mcp.McpProjectTemplateGenerationPort;
import cn.lgs.orbisops.application.mcp.McpProjectToolCatalogPort;
import cn.lgs.orbisops.application.mcp.McpRuntimeAuditPort;
import cn.lgs.orbisops.application.mcp.McpPolicyQueryService;
import cn.lgs.orbisops.application.mcp.McpRuntimeCatalogQueryService;
import cn.lgs.orbisops.application.mcp.McpRuntimeHistoryQueryService;
import cn.lgs.orbisops.application.mcp.McpRuntimeOperationsApplicationService;
import cn.lgs.orbisops.application.mcp.McpRuntimeOperationsPort;
import cn.lgs.orbisops.application.mcp.McpRuntimeViewMapper;
import cn.lgs.orbisops.application.mcp.McpRuntimePayloadSanitizerPort;
import cn.lgs.orbisops.application.mcp.McpToolSnapshotQueryService;
import cn.lgs.orbisops.application.mcp.McpToolSnapshotStorePort;
import cn.lgs.orbisops.application.mcp.McpSummaryApplicationService;
import cn.lgs.orbisops.application.mcp.McpSummaryPort;
import cn.lgs.orbisops.application.mcp.McpTemplateCatalogApplicationService;
import cn.lgs.orbisops.application.mcp.McpTemplatePort;
import cn.lgs.orbisops.application.mcp.McpTemplateProjectUsagePort;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.application.mcp.QueryMcpTemplateUseCase;
import cn.lgs.orbisops.application.mcp.ReviewMcpPolicyUseCase;
import cn.lgs.orbisops.application.mcp.SelectRuntimeMcpToolsQuery;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionPort;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpTemplateRepository;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsProgressiveMcpApplicationConfiguration {

    @Bean
    public McpSummaryApplicationService mcpSummaryApplicationService(
            McpProjectDirectoryPort projects,
            McpProjectToolCatalogPort catalog,
            SelectRuntimeMcpToolsQuery runtimeTools,
            IMcpRuntimeCatalogRepository runtimeCatalog) {
        return new McpSummaryApplicationService(
                projects, catalog, runtimeTools, runtimeCatalog, new McpJsonCodec());
    }

    @Bean
    public McpPolicyCommandApplicationService mcpPolicyCommandApplicationService(
            McpProjectDirectoryPort projects,
            IMcpToolPolicyRepository policies,
            McpToolSnapshotStorePort snapshots,
            McpAuditPort audit) {
        return new McpPolicyCommandApplicationService(
                projects, policies, snapshots, audit, new McpJsonCodec());
    }

    @Bean
    public McpDiscoveryApplicationService mcpDiscoveryApplicationService(
            McpProjectDirectoryPort projects,
            McpProjectToolCatalogPort catalog,
            SelectRuntimeMcpToolsQuery runtimeTools,
            IMcpRuntimeCatalogRepository runtimeCatalog,
            McpToolSnapshotStorePort snapshots,
            IMcpToolPolicyRepository policies,
            McpRuntimeAuditPort audit,
            McpPolicySuggestionPort suggestions) {
        return new McpDiscoveryApplicationService(projects, catalog, runtimeTools, runtimeCatalog,
                snapshots, policies, new McpJsonCodec(), audit, suggestions);
    }

    @Bean
    public McpRuntimeOperationsApplicationService mcpRuntimeOperationsApplicationService(
            SelectRuntimeMcpToolsQuery runtimeTools,
            McpProjectToolCatalogPort catalog,
            IMcpRuntimeCatalogRepository runtimeCatalog,
            McpDiscoveryPort discovery,
            McpRuntimeAuditPort audit,
            McpRuntimePayloadSanitizerPort payloadSanitizer,
            @Value("${orbisops.progressive-mcp.disclosure.enabled:true}") boolean progressiveDisclosureEnabled) {
        return new McpRuntimeOperationsApplicationService(runtimeTools, catalog, runtimeCatalog,
                discovery, audit, new McpJsonCodec(), payloadSanitizer, progressiveDisclosureEnabled);
    }

    @Bean
    public ProgressiveMcpProcessManager progressiveMcpProcessManager(
            McpSummaryPort summaries,
            McpDiscoveryPort discovery,
            McpRuntimeOperationsPort runtime) {
        return new ProgressiveMcpProcessManager(summaries, discovery, runtime);
    }

    @Bean
    public McpGovernanceApplicationService mcpGovernanceApplicationService(
            McpSummaryPort summaries,
            McpPolicyCommandPort policyCommands,
            McpRuntimeHistoryQueryService historyQueries,
            McpToolSnapshotQueryService snapshotQueries,
            McpPolicyQueryService policyQueries) {
        return new McpGovernanceApplicationService(
                summaries, policyCommands, historyQueries, snapshotQueries, policyQueries);
    }

    @Bean
    public DiscoverMcpToolsProcessManager discoverMcpToolsProcessManager(
            McpSummaryPort summaries,
            McpDiscoveryPort discovery,
            McpRuntimeOperationsPort runtime) {
        return new DiscoverMcpToolsProcessManager(summaries, discovery, runtime);
    }

    @Bean
    public ReviewMcpPolicyUseCase reviewMcpPolicyUseCase(McpPolicyCommandPort policyCommands) {
        return new ReviewMcpPolicyUseCase(policyCommands);
    }

    @Bean
    public McpRuntimeViewMapper mcpRuntimeViewMapper() {
        return new McpRuntimeViewMapper(new McpJsonCodec());
    }

    @Bean
    public McpRuntimeCatalogQueryService mcpRuntimeCatalogQueryService(
            McpProjectDirectoryPort projects,
            IMcpToolPolicyRepository policies,
            McpRuntimeViewMapper views) {
        return new McpRuntimeCatalogQueryService(projects, policies, views);
    }

    @Bean
    public McpRuntimeHistoryQueryService mcpRuntimeHistoryQueryService(
            McpProjectDirectoryPort projects,
            IMcpRuntimeCatalogRepository runtimeCatalog,
            McpRuntimeViewMapper views) {
        return new McpRuntimeHistoryQueryService(projects, runtimeCatalog, views);
    }

    @Bean
    public McpToolSnapshotQueryService mcpToolSnapshotQueryService(
            McpProjectDirectoryPort projects,
            IMcpToolSnapshotRepository snapshots,
            McpRuntimeViewMapper views) {
        return new McpToolSnapshotQueryService(projects, snapshots, views);
    }

    @Bean
    public McpPolicyQueryService mcpPolicyQueryService(
            McpProjectDirectoryPort projects,
            IMcpToolPolicyRepository policies,
            McpRuntimeViewMapper views) {
        return new McpPolicyQueryService(projects, policies, views);
    }

    @Bean
    public SelectRuntimeMcpToolsQuery selectRuntimeMcpToolsQuery(
            McpRuntimeCatalogQueryService catalog,
            McpRuntimeViewMapper views) {
        return new SelectRuntimeMcpToolsQuery(catalog, views);
    }

    @Bean
    public McpTemplateCatalogApplicationService mcpTemplateCatalogApplicationService(
            IMcpTemplateRepository repository) {
        return new McpTemplateCatalogApplicationService(repository);
    }

    @Bean
    public ManageMcpTemplateUseCase manageMcpTemplateUseCase(
            McpTemplatePort port,
            McpAuditPort auditPort,
            McpProjectTemplateGenerationPort generationService,
            ProjectWorkspaceProjectionPort workspaceProjection) {
        return new ManageMcpTemplateUseCase(
                port, auditPort, generationService, workspaceProjection);
    }

    @Bean
    public QueryMcpTemplateUseCase queryMcpTemplateUseCase(
            McpTemplatePort port,
            McpTemplateProjectUsagePort projectMcpCatalogService) {
        return new QueryMcpTemplateUseCase(port, projectMcpCatalogService);
    }
}
