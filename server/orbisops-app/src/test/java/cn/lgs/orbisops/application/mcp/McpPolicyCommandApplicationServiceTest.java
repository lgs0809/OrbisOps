package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpToolPolicyCommandMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpPolicyCommandApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T05:00:00Z");

    @Test
    void manualUpsertPersistsReviewedTypedPolicyAndAudits() {
        Fixture fixture = fixture(true);
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.empty());

        McpToolPolicy result = fixture.service.upsertToolPolicy(command(
                "policy-1", Map.of(
                "mcpId", "mcp-1",
                "toolId", "tool-1",
                "toolName", "search",
                "schemaHash", "schema-1",
                "readOnly", true,
                "riskLevel", "LOW",
                "allowedActions", List.of("SEARCH"))));

        assertEquals("policy-1", result.policyId());
        assertEquals(McpToolPolicyStatus.ACTIVE, result.status());
        assertEquals(McpToolPolicyReviewStatus.HUMAN_REVIEWED, result.reviewStatus());
        ArgumentCaptor<McpToolPolicy> captor = ArgumentCaptor.forClass(McpToolPolicy.class);
        verify(fixture.policies).save(captor.capture());
        McpToolPolicy saved = captor.getValue();
        assertEquals("policy-1", saved.policyId());
        assertEquals("admin", saved.reviewedBy());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), saved.reviewedAt());
        assertTrue(saved.readOnly());
        verify(fixture.audit).record(eq("project-1"), eq("mcp-tool-policy"), eq("upsert"),
                eq("policy-1"), isNull(), any());
    }

    @Test
    void approvalFailsFastWhenReviewedToolIdentityIsMissing() {
        Fixture fixture = fixture(true);
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.of(policy("PENDING_REVIEW", "AI_SUGGESTED", "")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.approveToolPolicy(command(
                        "policy-1", Map.of("toolName", " "))));

        assertTrue(error.getMessage().contains("必须提供 toolName"));
        verify(fixture.snapshots, never()).latestReviewableSnapshot(any(), any(), any(), any());
        verify(fixture.policies, never()).save(any());
    }

    @Test
    void approvalRejectsUnknownEffectAndActionsBeforeSnapshotLookup() {
        Fixture fixture = fixture(true);
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.of(policy("PENDING_REVIEW", "AI_SUGGESTED", "")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.approveToolPolicy(command("policy-1", Map.of(
                        "effectType", "UNKNOWN",
                        "effectScope", "UNKNOWN",
                        "mutability", "UNKNOWN",
                        "allowedActions", List.of("UNKNOWN_MUTATING")))));

        assertTrue(error.getMessage().contains("MCP_POLICY_CLASSIFICATION_REQUIRED"));
        verify(fixture.snapshots, never()).latestReviewableSnapshot(any(), any(), any(), any());
        verify(fixture.policies, never()).save(any());
    }

    @Test
    void approvalRejectsUnsafeCoreDisclosureBeforeSnapshotLookup() {
        Fixture fixture = fixture(true);
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.of(policy("PENDING_REVIEW", "AI_SUGGESTED", "")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.approveToolPolicy(command("policy-1", Map.of(
                        "effectType", "MUTATE_TARGET_RESOURCE",
                        "effectScope", "PRODUCTION",
                        "mutability", "PROD_MUTATING",
                        "readOnly", false,
                        "investigateAllowed", false,
                        "requiresApprovedPackage", true,
                        "requiresHumanApproval", true,
                        "riskLevel", "HIGH",
                        "allowedActions", List.of("RESTART"),
                        "disclosureTier", "CORE"))));

        assertTrue(error.getMessage().contains("MCP_POLICY_CORE_REQUIRES_LOW_RISK_READ_ONLY"));
        verify(fixture.snapshots, never()).latestReviewableSnapshot(any(), any(), any(), any());
        verify(fixture.policies, never()).save(any());
    }

    @Test
    void approvalFailsClosedBeforeRemoteInputSchemaIsHydrated() {
        Fixture fixture = fixture(true);
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.of(policy("PENDING_REVIEW", "AI_SUGGESTED", "")));
        when(fixture.snapshots.latestReviewableSnapshot(
                "project-1", "mcp-1", "tool-1", "search"))
                .thenReturn(Optional.of(snapshot("schema-1", false)));

        SecurityException error = assertThrows(SecurityException.class,
                () -> fixture.service.approveToolPolicy(command("policy-1", Map.of())));

        assertTrue(error.getMessage().contains("MCP_TOOL_SCHEMA_NOT_HYDRATED"));
        verify(fixture.policies, never()).save(any());
    }

    @Test
    void approvalRejectsPolicyWhenLatestSchemaHashChanged() {
        Fixture fixture = fixture(true);
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.of(policy("PENDING_REVIEW", "AI_SUGGESTED", "")));
        when(fixture.snapshots.latestReviewableSnapshot(
                "project-1", "mcp-1", "tool-1", "search"))
                .thenReturn(Optional.of(snapshot("schema-2", true)));

        SecurityException error = assertThrows(SecurityException.class,
                () -> fixture.service.approveToolPolicy(command("policy-1", Map.of())));

        assertTrue(error.getMessage().contains("MCP_POLICY_STALE"));
        verify(fixture.policies, never()).save(any());
    }

    @Test
    void approvalPublishesLowRiskReadOnlyCorePolicyAndPreservesDoubleAudit() {
        Fixture fixture = fixture(true);
        McpToolPolicy before = policy("PENDING_REVIEW", "AI_SUGGESTED", "");
        McpToolPolicy after = policy("ACTIVE", "HUMAN_REVIEWED", "admin");
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.of(before), Optional.of(after));
        when(fixture.snapshots.latestReviewableSnapshot(
                "project-1", "mcp-1", "tool-1", "search"))
                .thenReturn(Optional.of(snapshot("schema-1", true)));

        McpToolPolicy result = fixture.service.approveToolPolicy(command(
                "policy-1", Map.of("disclosureTier", "CORE")));

        assertEquals(McpToolPolicyStatus.ACTIVE, result.status());
        assertEquals(McpToolPolicyReviewStatus.HUMAN_REVIEWED, result.reviewStatus());
        ArgumentCaptor<McpToolPolicy> captor = ArgumentCaptor.forClass(McpToolPolicy.class);
        verify(fixture.policies).save(captor.capture());
        assertEquals("admin", captor.getValue().reviewedBy());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), captor.getValue().reviewedAt());
        assertEquals("{\"disclosureTier\":\"CORE\"}", captor.getValue().metadataJson());
        verify(fixture.audit).record(eq("project-1"), eq("mcp-tool-policy"), eq("upsert"),
                eq("policy-1"), isNull(), any());
        verify(fixture.audit).record(eq("project-1"), eq("mcp-tool-policy"), eq("approve"),
                eq("policy-1"), any(), any());
    }

    @Test
    void rejectionPersistsReasonAndAuditsStatusTransition() {
        Fixture fixture = fixture(true);
        McpToolPolicy before = policy("PENDING_REVIEW", "AI_SUGGESTED", "");
        McpToolPolicy after = policy("REJECTED", "HUMAN_REVIEWED", "admin");
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.of(before), Optional.of(after));
        when(fixture.policies.updateReviewStatus(
                "project-1", "policy-1",
                McpToolPolicyStatus.REJECTED,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "admin", "{\"reviewReason\":\"insufficient evidence\"}"))
                .thenReturn(true);

        McpToolPolicy result = fixture.service.rejectToolPolicy(command(
                "policy-1", Map.of("reason", "insufficient evidence")));

        assertEquals(McpToolPolicyStatus.REJECTED, result.status());
        verify(fixture.policies).updateReviewStatus(
                "project-1", "policy-1",
                McpToolPolicyStatus.REJECTED,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "admin", "{\"reviewReason\":\"insufficient evidence\"}");
        verify(fixture.audit).record(eq("project-1"), eq("mcp-tool-policy"), eq("reject"),
                eq("policy-1"), any(), any());
    }

    @Test
    void disableReportsRepositoryConflictWithoutReturningAFalseTerminalState() {
        Fixture fixture = fixture(true);
        when(fixture.policies.findById("project-1", "policy-1"))
                .thenReturn(Optional.of(policy("ACTIVE", "HUMAN_REVIEWED", "reviewer")));
        when(fixture.policies.updateReviewStatus(
                "project-1", "policy-1",
                McpToolPolicyStatus.DISABLED,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "admin", "{}"))
                .thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.service.disableToolPolicy(command("policy-1", Map.of())));

        assertTrue(error.getMessage().contains("MCP_TOOL_POLICY_UPDATE_CONFLICT"));
    }

    @Test
    void missingProjectFailsBeforePolicyStoreAccess() {
        Fixture fixture = fixture(false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.upsertToolPolicy(new OpsMcpToolPolicyCommandMapper().mutation(
                        "missing-project", "policy-1", "admin", Map.of(
                        "mcpId", "mcp-1", "toolName", "search", "schemaHash", "schema-1"))));

        assertTrue(error.getMessage().contains("项目不存在"));
        verify(fixture.policies, never()).save(any());
        verify(fixture.policies, never()).findById(any(), any());
        verify(fixture.policies, never()).findActiveReviewed(any(), any(), any(), any());
    }

    private McpCommands.PolicyMutation command(String policyId, Map<String, Object> request) {
        return new OpsMcpToolPolicyCommandMapper().mutation(
                "project-1", policyId, "admin", request);
    }

    private Fixture fixture(boolean projectExists) {
        McpProjectDirectoryPort projects = mock(McpProjectDirectoryPort.class);
        when(projects.exists(projectExists ? "project-1" : "missing-project")).thenReturn(projectExists);
        IMcpToolPolicyRepository policies = mock(IMcpToolPolicyRepository.class);
        when(policies.available()).thenReturn(true);
        McpToolSnapshotStorePort snapshots = mock(McpToolSnapshotStorePort.class);
        when(snapshots.available()).thenReturn(true);
        McpAuditPort audit = mock(McpAuditPort.class);
        McpJsonCodec jsonCodec = new McpJsonCodec();
        McpPolicyCommandApplicationService service = new McpPolicyCommandApplicationService(
                projects, policies, snapshots, audit, jsonCodec, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, policies, snapshots, audit);
    }

    private McpReviewableToolSnapshot snapshot(
            String schemaHash,
            boolean schemaHydrated) {
        return new McpReviewableToolSnapshot(schemaHash, schemaHydrated);
    }

    private McpToolPolicy policy(String status, String reviewStatus, String reviewedBy) {
        return new McpToolPolicy(1, "policy-1", "project-1", "mcp-1", "tool-1", "search",
                "schema-1", "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY", "READ_ONLY",
                "[\"SEARCH\"]", cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.LOW,
                true, true, true, true, false, false,
                false, false, "{}",
                cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus.require(status),
                cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus.require(reviewStatus),
                reviewedBy, null,
                reviewStatus.contains("SUGGESTED") ? "system" : "", null, "{}", null, null);
    }

    private record Fixture(McpPolicyCommandApplicationService service,
                           IMcpToolPolicyRepository policies,
                           McpToolSnapshotStorePort snapshots,
                           McpAuditPort audit) {
    }

}
