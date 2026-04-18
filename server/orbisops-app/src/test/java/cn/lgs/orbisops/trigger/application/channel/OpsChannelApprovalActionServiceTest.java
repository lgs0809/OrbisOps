package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeAuditPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionRecord;
import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionTokenService;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalOutcome;
import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalUseCase;
import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ChangePackageCurrentReadPort;
import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelApprovalActionServiceTest {

    private ChannelApprovalActionTokenService tokens;
    private ChannelRuntimeReadPort channels;
    private ChangePackageCurrentReadPort packages;
    private ChannelIdentityDirectoryPort identities;
    private AdminUserCatalogPort users;
    private OpsChangePackagePermissionService permissions;
    private ChangePackageApprovalUseCase approval;
    private ChannelRuntimeAuditPort audit;
    private OpsChannelApprovalActionService service;

    @BeforeEach
    void setUp() {
        tokens = mock(ChannelApprovalActionTokenService.class);
        channels = mock(ChannelRuntimeReadPort.class);
        packages = mock(ChangePackageCurrentReadPort.class);
        identities = mock(ChannelIdentityDirectoryPort.class);
        users = mock(AdminUserCatalogPort.class);
        permissions = mock(OpsChangePackagePermissionService.class);
        approval = mock(ChangePackageApprovalUseCase.class);
        audit = mock(ChannelRuntimeAuditPort.class);
        service = new OpsChannelApprovalActionService(tokens, channels, packages, identities, users, permissions, approval, audit);

        when(packages.available()).thenReturn(true);
        when(channels.findById("channel-1")).thenReturn(Optional.of(channel()));
        when(packages.find("cp-1")).thenReturn(Optional.of(current(ChangePackageStatus.REVIEWING, 3, "hash-3")));
        ChannelIdentityRecord identity = identity();
        when(channels.findIdentity("channel-1", "external-user")).thenReturn(Optional.of(identity));
        when(identities.isTrusted("project-1", identity)).thenReturn(true);
        when(users.findByUserId("user-1")).thenReturn(user(AdminUserRole.USER));
    }

    @Test
    void terminalApproveUsesFrozenVersionHashAndConsumesSharedAction() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        when(approval.approve(any())).thenReturn(new ChangePackageApprovalOutcome(
                "cp-1", ChangePackageStatus.APPROVED, 3, "hash-3", 1, 1));

        var result = service.execute("channel-1", envelope("opaque-action-value"));

        ArgumentCaptor<ChangePackageCommands.Approve> command = ArgumentCaptor.forClass(ChangePackageCommands.Approve.class);
        verify(permissions).assertCanApprovePackage(eq("project-1"), any());
        verify(tokens).claimForActor(action, "user-1");
        verify(approval).approve(command.capture());
        assertEquals(3, command.getValue().version());
        assertEquals("hash-3", command.getValue().packageHash());
        assertEquals("user-1", command.getValue().actor());
        assertFalse(command.getValue().approvalContext().adminConfirmation());
        verify(tokens).complete(action, "user-1");
        assertTrue(result.handled());
        assertEquals("APPROVED", result.status());
        assertFalse(result.waitingForMoreApprovals());
    }

    @Test
    void firstOfTwoApproversKeepsSharedActionActiveForNextApprover() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        when(approval.approve(any())).thenReturn(new ChangePackageApprovalOutcome(
                "cp-1", ChangePackageStatus.REVIEWING, 3, "hash-3", 2, 1));

        var result = service.execute("channel-1", envelope("opaque-action-value"));

        verify(tokens).claimForActor(action, "user-1");
        verify(tokens, never()).complete(any(), any());
        verify(tokens, never()).revoke(any(), any(), any());
        assertTrue(result.waitingForMoreApprovals());
        assertEquals(2, result.requiredApprovals());
        assertEquals(1, result.approvedCount());
    }

    @Test
    void rejectUsesAuthoritativeRejectUseCaseAndConsumesSharedAction() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.REJECT, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        when(approval.reject(any())).thenReturn(new ChangePackageApprovalOutcome(
                "cp-1", ChangePackageStatus.REJECTED, 3, "hash-3", 1, 0));

        var result = service.execute("channel-1", envelope("opaque-action-value"));

        verify(permissions).assertCanRejectPackage(eq("project-1"), any());
        verify(tokens).claimForActor(action, "user-1");
        verify(approval).reject(any());
        verify(approval, never()).approve(any());
        verify(tokens).complete(action, "user-1");
        assertEquals(ChannelApprovalActionRecord.Decision.REJECT, result.decision());
        assertEquals("REJECTED", result.status());
    }

    @Test
    void stalePackageVersionRevokesSharedCardBeforeIdentityOrApproval() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 2, "hash-2");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.execute("channel-1", envelope("opaque-action-value")));

        assertEquals("CHANNEL_APPROVAL_ACTION_STALE_VERSION", failure.getMessage());
        verify(channels, never()).findIdentity(any(), any());
        verify(tokens).revoke(action, "system", "CHANNEL_APPROVAL_ACTION_STALE_VERSION");
        verify(tokens, never()).claimForActor(any(), any());
        verify(approval, never()).approve(any());
    }

    @Test
    void alreadyResolvedPackageRevokesOldSharedCard() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        when(packages.find("cp-1")).thenReturn(Optional.of(current(ChangePackageStatus.CLOSED, 3, "hash-3")));

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.execute("channel-1", envelope("opaque-action-value")));

        assertEquals("CHANNEL_APPROVAL_ACTION_STALE_STATE", failure.getMessage());
        verify(tokens).revoke(action, "system", "CHANNEL_APPROVAL_ACTION_STALE_STATE");
        verify(approval, never()).approve(any());
    }

    @Test
    void removedProjectMembershipCannotConsumeOrRevokeSharedCard() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        when(identities.isTrusted("project-1", identity())).thenReturn(false);

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.execute("channel-1", envelope("opaque-action-value")));

        assertEquals("CHANNEL_APPROVAL_IDENTITY_NOT_AUTHORIZED", failure.getMessage());
        verify(permissions, never()).assertCanApprovePackage(any(), any());
        verify(tokens, never()).claimForActor(any(), any());
        verify(tokens, never()).revoke(any(), any(), any());
        verify(tokens, never()).complete(any(), any());
        verify(approval, never()).approve(any());
    }

    @Test
    void unmappedExternalUserCannotDestroySomeoneElsesSharedApprovalCard() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        when(channels.findIdentity("channel-1", "external-user")).thenReturn(Optional.empty());

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.execute("channel-1", envelope("opaque-action-value")));

        assertEquals("CHANNEL_APPROVAL_IDENTITY_MAPPING_REQUIRED", failure.getMessage());
        verify(tokens, never()).claimForActor(any(), any());
        verify(tokens, never()).revoke(any(), any(), any());
        verify(approval, never()).approve(any());
    }

    @Test
    void duplicateClickBySamePlatformActorIsRejectedWithoutRevokingSharedCard() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        doThrow(new SecurityException("CHANNEL_APPROVAL_ACTION_REPLAYED_BY_ACTOR"))
                .when(tokens).claimForActor(action, "user-1");

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.execute("channel-1", envelope("opaque-action-value")));

        assertEquals("CHANNEL_APPROVAL_ACTION_REPLAYED_BY_ACTOR", failure.getMessage());
        verify(tokens, never()).revoke(any(), any(), any());
        verify(tokens, never()).complete(any(), any());
        verify(approval, never()).approve(any());
    }

    @Test
    void authoritativeFailureAfterActorClaimReleasesClaimForSafeRetry() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        when(approval.approve(any())).thenThrow(new IllegalStateException("temporary approval store failure"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.execute("channel-1", envelope("opaque-action-value")));

        assertEquals("temporary approval store failure", failure.getMessage());
        verify(tokens).claimForActor(action, "user-1");
        verify(tokens).releaseActorClaim(action, "user-1");
        verify(tokens, never()).revoke(any(), any(), any());
        verify(tokens, never()).complete(any(), any());
    }

    @Test
    void adminConfirmationIsDerivedFromCurrentPlatformRoleNotChannelPayload() {
        ChannelApprovalActionRecord action = action(ChannelApprovalActionRecord.Decision.APPROVE, 3, "hash-3");
        when(tokens.resolve("opaque-action-value")).thenReturn(action);
        when(users.findByUserId("user-1")).thenReturn(user(AdminUserRole.ADMIN));
        when(approval.approve(any())).thenReturn(new ChangePackageApprovalOutcome(
                "cp-1", ChangePackageStatus.APPROVED, 3, "hash-3", 1, 1));

        service.execute("channel-1", envelope("opaque-action-value"));

        ArgumentCaptor<ChangePackageCommands.Approve> command = ArgumentCaptor.forClass(ChangePackageCommands.Approve.class);
        verify(approval).approve(command.capture());
        assertTrue(command.getValue().approvalContext().adminConfirmation());
    }

    @Test
    void malformedOrdinaryProviderActionFallsThroughInsteadOfBecomingApprovalFailure() {
        when(tokens.find("ordinary-action")).thenThrow(new SecurityException("CHANNEL_APPROVAL_ACTION_TOKEN_INVALID"));

        Optional<OpsChannelApprovalActionService.ActionOutcome> result = service.tryExecute(
                "channel-1", envelope("ordinary-action"));

        assertTrue(result.isEmpty());
        verify(tokens, never()).resolve(any());
        verify(approval, never()).approve(any());
        verify(approval, never()).reject(any());
    }

    private ChannelApprovalActionRecord action(ChannelApprovalActionRecord.Decision decision, int version, String hash) {
        return new ChannelApprovalActionRecord(
                "action-1", "action-hash", "channel-1", "project-1", "cp-1", version, hash, decision,
                ChannelApprovalActionRecord.Status.ACTIVE, "issuer", Instant.now().plusSeconds(300), Instant.now(),
                "", null, "");
    }

    private ChannelInteractiveActionEnvelope envelope(String actionKey) {
        ChannelConversationRef conversation = new ChannelConversationRef("room-1", ChannelConversationRef.ConversationKind.GROUP);
        return new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("approve", "Approve", actionKey, ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal("external-user", "Alice", ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef("message-1", conversation), Instant.now(), "message-1:approve");
    }

    private ChannelRecord channel() {
        return new ChannelRecord("channel-1", "project-1", ExecutionBinding.react(), "WeCom", "WECOM",
                "credential-ref", Map.of("botId", "bot-1"), ChannelAccessPolicy.DENY_UNKNOWN,
                ChannelStatus.ACTIVE, "admin", null, null);
    }

    private ChannelIdentityRecord identity() {
        return new ChannelIdentityRecord("mapping-1", "channel-1", "project-1", "external-user",
                "user-1", "alice", ChannelStatus.ACTIVE, 1, "admin", null, null);
    }

    private AdminUserAccount user(AdminUserRole role) {
        return new AdminUserAccount(1L, "user-1", "alice", "", role, 1, null, null);
    }

    private ChangePackageCurrent current(ChangePackageStatus status, int version, String hash) {
        ChangePackagePointer pointer = new ChangePackagePointer("cp-1", status, version, hash, 0, "");
        return new ChangePackageCurrent(
                1L, pointer, "session-1", "incident-1", "project-1", "agent-1", 1,
                ChangePackageType.MCP_OPERATION_PACKAGE, ChangePackageCurrentState.fromSnapshot(Map.of("riskLevel", "LOW")),
                null, "", "creator", "", null, null, null);
    }
}
