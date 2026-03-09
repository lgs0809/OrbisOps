package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolSnapshotRepository;
import cn.lgs.orbisops.domain.mcp.model.McpRuntimeActivation;
import cn.lgs.orbisops.domain.mcp.model.McpToolCall;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyAdminRecord;
import cn.lgs.orbisops.domain.mcp.model.McpToolSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelectRuntimeMcpToolsQueryTest {

    private McpProjectDirectoryPort projects;
    private IMcpRuntimeCatalogRepository runtime;
    private IMcpToolSnapshotRepository snapshots;
    private IMcpToolPolicyRepository policies;
    private SelectRuntimeMcpToolsQuery selection;
    private McpPolicyQueryService policyQueries;
    private McpToolSnapshotQueryService snapshotQueries;
    private McpRuntimeHistoryQueryService historyQueries;

    @BeforeEach
    void setUp() {
        projects = mock(McpProjectDirectoryPort.class);
        runtime = mock(IMcpRuntimeCatalogRepository.class);
        snapshots = mock(IMcpToolSnapshotRepository.class);
        policies = mock(IMcpToolPolicyRepository.class);
        when(projects.exists("project-1")).thenReturn(true);
        when(runtime.available()).thenReturn(true);
        when(snapshots.available()).thenReturn(true);
        when(policies.available()).thenReturn(true);
        McpRuntimeViewMapper views = new McpRuntimeViewMapper(new McpJsonCodec());
        selection = new SelectRuntimeMcpToolsQuery(
                new McpRuntimeCatalogQueryService(projects, policies, views), views);
        policyQueries = new McpPolicyQueryService(projects, policies, views);
        snapshotQueries = new McpToolSnapshotQueryService(projects, snapshots, views);
        historyQueries = new McpRuntimeHistoryQueryService(projects, runtime, views);
    }

    @Test
    void runtimeCatalogAppliesGovernanceAllowedAndBlockedFilters() {
        when(policies.findRuntimeExecutable("project-1", "logs-mcp")).thenReturn(List.of(
                policy("policy-search", "search_logs", "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ",
                        "READ_ONLY", "LOW", true, true, false, false, "CORE"),
                policy("policy-health", "health", "NO_EFFECT", "TARGET_RESOURCE_READ",
                        "READ_ONLY", "LOW", true, true, false, false, "CORE"),
                policy("policy-restart", "restart_service", "MUTATE_TARGET_RESOURCE", "PRODUCTION",
                        "PROD_MUTATING", "HIGH", false, false, false, true, "EXTENSION")));

        List<Map<String, Object>> result = selection.runtimeCatalog(
                "project-1", "logs-mcp", List.of("search_logs", "restart_service"), List.of("health"));

        assertEquals(1, result.size());
        assertEquals("search_logs", result.get(0).get("toolName"));
        assertEquals("CORE", result.get(0).get("disclosureTier"));
        verify(policies).findRuntimeExecutable("project-1", "logs-mcp");
    }

    @Test
    void landingCatalogExposesLandAllowedApprovedPackageToolsButPrepareDoesNot() {
        when(policies.findRuntimeExecutable("project-1", "logs-mcp")).thenReturn(List.of(
                policy("policy-search", "search_logs", "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ",
                        "READ_ONLY", "LOW", true, true, false, false, "CORE"),
                landingPolicy("policy-restart", "restart_service")));

        List<Map<String, Object>> prepare = selection.runtimeExecutableTools(
                "project-1", "logs-mcp", List.of(), List.of(), "PREPARE");
        List<Map<String, Object>> prepareChange = selection.runtimeExecutableTools(
                "project-1", "logs-mcp", List.of(), List.of(), "PREPARE_CHANGE");
        List<Map<String, Object>> landing = selection.runtimeExecutableTools(
                "project-1", "logs-mcp", List.of(), List.of(), "LANDING");

        assertEquals(List.of("search_logs"), prepare.stream().map(item -> item.get("toolName")).toList());
        assertEquals(List.of("search_logs", "restart_service"),
                prepareChange.stream().map(item -> item.get("toolName")).toList());
        assertEquals(List.of("restart_service"), landing.stream().map(item -> item.get("toolName")).toList());
    }

    @Test
    void policyProjectionParsesJsonAndPreservesNullableTimes() {
        McpToolPolicy policy = policy("policy-search", "search_logs", "READ_EXTERNAL_STATE",
                "TARGET_RESOURCE_READ", "READ_ONLY", "LOW", true, true, false, false, "CORE");
        when(policies.findAllForAdmin("project-1", 20)).thenReturn(List.of(adminPolicy(policy)));

        Map<String, Object> result = policyQueries.policies("project-1", 20).get(0);

        assertEquals(List.of("READ"), result.get("allowedActions"));
        assertEquals(Map.of("maxLimit", 500), result.get("argumentPolicy"));
        assertEquals("CORE", result.get("disclosureTier"));
        assertEquals("Search logs", result.get("description"));
        assertEquals(null, result.get("reviewedAt"));
        assertEquals(null, result.get("createTime"));
    }

    @Test
    void snapshotAndActivationViewsDecodeJsonWithoutTriggerService() {
        McpToolSnapshot snapshot = new McpToolSnapshot(
                "snapshot-1", "project-1", "logs-mcp", "tool-1", "search_logs", "schema-1",
                "{\"type\":\"object\"}", "{\"source\":\"remote\"}", true, "ACTIVE", null, null);
        McpRuntimeActivation activation = new McpRuntimeActivation(
                "activation-1", "project-1", "run-1", "session-1", "agent-1", "logs-mcp",
                "search_logs", "schema-1", "EXTENSION", "ACTIVE", LocalDateTime.now().plusMinutes(5),
                "{\"actor\":\"alice\"}", null, null);
        when(snapshots.findAll("project-1", 10)).thenReturn(List.of(snapshot));
        when(runtime.findActivations("project-1", 10)).thenReturn(List.of(activation));

        assertEquals(Map.of("type", "object"),
                snapshotQueries.snapshots("project-1", 10).get(0).get("schema"));
        assertEquals(Map.of("actor", "alice"),
                historyQueries.runtimeActivations("project-1", 10).get(0).get("metadata"));
    }

    @Test
    void callLookupUsesTypedRuntimeRepositoryAndRejectsMissingCall() {
        McpToolCall call = new McpToolCall(
                "call-1", "project-1", "agent-1", "node-1", "run-1", "tool-1", "logs-mcp",
                "search_logs", "LOW", true, "SUCCEEDED", "{\"q\":\"error\"}",
                "{\"count\":3}", 12L, "", null);
        when(runtime.findToolCall("project-1", "call-1")).thenReturn(Optional.of(call));
        when(runtime.findToolCall("project-1", "missing")).thenReturn(Optional.empty());

        Map<String, Object> result = historyQueries.call("project-1", "call-1");

        assertEquals(Map.of("q", "error"), result.get("input"));
        assertEquals(Map.of("count", 3), result.get("output"));
        assertThrows(IllegalArgumentException.class,
                () -> historyQueries.call("project-1", "missing"));
    }

    @Test
    void optionalStoresDegradeToEmptyButRuntimeStoreFailsClosed() {
        when(policies.available()).thenReturn(false);
        when(snapshots.available()).thenReturn(false);

        assertEquals(List.of(), policyQueries.policies("project-1", 10));
        assertEquals(List.of(), snapshotQueries.snapshots("project-1", 10));
        assertEquals(List.of(), selection.runtimeCatalog("project-1", "logs-mcp", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> policyQueries.policies("project-1", 0));
        assertThrows(IllegalArgumentException.class, () -> snapshotQueries.snapshots("project-1", 1001));

        when(runtime.available()).thenReturn(false);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> historyQueries.runtimeActivations("project-1", 10));
        assertEquals("MCP_RUNTIME_CATALOG_STORE_UNAVAILABLE", failure.getMessage());
    }

    @Test
    void projectMustExistBeforeRepositoryQuery() {
        when(projects.exists("missing")).thenReturn(false);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> policyQueries.policies("missing", 10));

        assertEquals("项目不存在：missing", failure.getMessage());
    }

    private McpToolPolicyAdminRecord adminPolicy(McpToolPolicy policy) {
        return new McpToolPolicyAdminRecord(
                policy.id(), policy.policyId(), policy.projectId(), policy.mcpId(), policy.toolId(), policy.toolName(),
                policy.schemaHash(), policy.effectType(), policy.effectScope(), policy.mutability(), policy.capability(),
                policy.allowedActionsJson(), policy.riskLevel().name(), policy.readOnly(), policy.investigateAllowed(),
                policy.prepareAllowed(), policy.landAllowed(), policy.requiresApprovedPackage(),
                policy.requiresHumanApproval(), policy.requiresDryRun(),
                policy.requiresRollbackPlan(), policy.argumentPolicyJson(), policy.status().name(),
                policy.reviewStatus().name(), policy.reviewedBy(), policy.reviewedAt(), policy.suggestedBy(),
                policy.suggestedAt(), policy.metadataJson(), policy.createTime(), policy.updateTime());
    }

    private McpToolPolicy policy(String policyId,
                                 String toolName,
                                 String effectType,
                                 String effectScope,
                                 String mutability,
                                 String risk,
                                 boolean readOnly,
                                 boolean investigate,
                                 boolean prepare,
                                 boolean requiresPackage,
                                 String tier) {
        return new McpToolPolicy(
                1L, policyId, "project-1", "logs-mcp", "tool-1", toolName, "schema-1",
                effectType, effectScope, mutability, readOnly ? "READ_ONLY" : "MUTATING", "[\"READ\"]",
                cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.failClosed(risk),
                readOnly, investigate, prepare, false, requiresPackage, requiresPackage,
                false, !readOnly, "{\"maxLimit\":500}",
                cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus.ACTIVE,
                cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "admin", null, "system", null,
                "{\"disclosureTier\":\"" + tier + "\",\"description\":\"Search logs\"}",
                null, null);
    }

    private McpToolPolicy landingPolicy(String policyId, String toolName) {
        return new McpToolPolicy(
                2L, policyId, "project-1", "logs-mcp", "tool-1", toolName, "schema-landing",
                "EXECUTE_EXTERNAL_ACTION", "PRODUCTION", "PROD_MUTATING", "MUTATING", "[\"RESTART\"]",
                cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.HIGH,
                false, false, false, true, true, true,
                true, true, "{\"allowedServices\":[\"order-service\"]}",
                cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus.ACTIVE,
                cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "admin", null, "system", null,
                "{\"disclosureTier\":\"EXTENSION\",\"description\":\"Restart service\"}",
                null, null);
    }
}
