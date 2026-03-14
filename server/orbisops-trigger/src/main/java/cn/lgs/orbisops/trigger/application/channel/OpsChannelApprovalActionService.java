package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeAuditPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionRecord;
import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionTokenService;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalContext;
import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalOutcome;
import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalUseCase;
import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ChangePackageCurrentReadPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageRejectionRequest;
import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Channel is only a command surface. Authoritative approval state remains ChangePackageApprovalUseCase. */
@Service
public final class OpsChannelApprovalActionService {

    private final ChannelApprovalActionTokenService tokens;
    private final ChannelRuntimeReadPort channels;
    private final ChangePackageCurrentReadPort packages;
    private final ChannelIdentityDirectoryPort identities;
    private final AdminUserCatalogPort users;
    private final OpsChangePackagePermissionService permissions;
    private final ChangePackageApprovalUseCase approval;
    private final ChannelRuntimeAuditPort audit;

    public OpsChannelApprovalActionService(ChannelApprovalActionTokenService tokens,
                                           ChannelRuntimeReadPort channels,
                                           ChangePackageCurrentReadPort packages,
                                           ChannelIdentityDirectoryPort identities,
                                           AdminUserCatalogPort users,
                                           OpsChangePackagePermissionService permissions,
                                           ChangePackageApprovalUseCase approval,
                                           ChannelRuntimeAuditPort audit) {
        this.tokens = required(tokens, "CHANNEL_APPROVAL_TOKEN_SERVICE_REQUIRED");
        this.channels = required(channels, "CHANNEL_REPOSITORY_REQUIRED");
        this.packages = required(packages, "CHANGE_PACKAGE_CURRENT_REPOSITORY_REQUIRED");
        this.identities = required(identities, "CHANNEL_IDENTITY_DIRECTORY_REQUIRED");
        this.users = required(users, "CHANNEL_USER_DIRECTORY_REQUIRED");
        this.permissions = required(permissions, "CHANGE_PACKAGE_PERMISSION_SERVICE_REQUIRED");
        this.approval = required(approval, "CHANGE_PACKAGE_APPROVAL_USE_CASE_REQUIRED");
        this.audit = required(audit, "CHANNEL_RUNTIME_AUDIT_PORT_REQUIRED");
    }

    public ApprovalActions issue(String channelId, String packageId, String issuer) {
        ChannelRecord channel = activeChannel(channelId);
        ChangePackageCurrent current = current(packageId);
        if (!channel.projectId().equals(current.projectId())) throw new SecurityException("CHANNEL_APPROVAL_PROJECT_MISMATCH");
        if (current.status() != ChangePackageStatus.REVIEWING) throw new IllegalStateException("CHANNEL_APPROVAL_PACKAGE_NOT_REVIEWING");
        ChannelApprovalActionTokenService.IssuedAction approve = tokens.issue(new ChannelApprovalActionTokenService.IssueCommand(
                channel.channelId(), current.projectId(), current.packageId(), current.version(), current.packageHash(),
                ChannelApprovalActionRecord.Decision.APPROVE, requiredText(issuer, "CHANNEL_APPROVAL_ISSUER_REQUIRED"), Duration.ofMinutes(15)));
        ChannelApprovalActionTokenService.IssuedAction reject = tokens.issue(new ChannelApprovalActionTokenService.IssueCommand(
                channel.channelId(), current.projectId(), current.packageId(), current.version(), current.packageHash(),
                ChannelApprovalActionRecord.Decision.REJECT, requiredText(issuer, "CHANNEL_APPROVAL_ISSUER_REQUIRED"), Duration.ofMinutes(15)));
        return new ApprovalActions(current.packageId(), current.version(), current.packageHash(), List.of(
                new ChannelInteractiveAction(approve.actionId(), "Approve", approve.opaqueActionToken(), ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelInteractiveAction(reject.actionId(), "Reject", reject.opaqueActionToken(), ChannelInteractiveAction.ActionStyle.DANGER)));
    }

    public java.util.Optional<ActionOutcome> tryExecute(String channelId, ChannelInteractiveActionEnvelope envelope) {
        if (envelope == null) return java.util.Optional.empty();
        try {
            if (tokens.find(envelope.action().opaqueActionToken()).isEmpty()) return java.util.Optional.empty();
        } catch (IllegalArgumentException | SecurityException notApprovalAction) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(execute(channelId, envelope));
    }

    public ActionOutcome execute(String channelId, ChannelInteractiveActionEnvelope envelope) {
        if (envelope == null) throw new IllegalArgumentException("CHANNEL_ACTION_REQUIRED");
        ChannelApprovalActionRecord action = tokens.resolve(envelope.action().opaqueActionToken());
        if (!action.channelId().equals(requiredText(channelId, "CHANNEL_ID_REQUIRED"))) {
            SecurityException failure = new SecurityException("CHANNEL_APPROVAL_ACTION_CHANNEL_MISMATCH");
            auditFailure(action, "", failure);
            throw failure;
        }

        ChangePackageCurrent current = current(action.packageId());
        try {
            assertFrozenAction(action, current);
        } catch (RuntimeException stale) {
            tokens.revoke(action, "system", safe(stale.getMessage()));
            auditFailure(action, "", stale);
            throw stale;
        }

        ChannelIdentityRecord identity;
        AdminAuthService.AuthPrincipal principal;
        String actor;
        try {
            identity = trustedIdentity(action, envelope.actor().externalPrincipalId());
            principal = principal(identity);
            actor = principal.userId().isBlank() ? principal.username() : principal.userId();
            if (action.decision() == ChannelApprovalActionRecord.Decision.APPROVE) {
                permissions.assertCanApprovePackage(action.projectId(), principal);
            } else {
                permissions.assertCanRejectPackage(action.projectId(), principal);
            }
        } catch (RuntimeException unauthorized) {
            auditFailure(action, "", unauthorized);
            throw unauthorized;
        }

        boolean actorClaimed = false;
        ChangePackageApprovalOutcome result;
        try {
            tokens.claimForActor(action, actor);
            actorClaimed = true;
            if (action.decision() == ChannelApprovalActionRecord.Decision.APPROVE) {
                result = approval.approve(new ChangePackageCommands.Approve(
                        action.packageId(), action.packageVersion(), action.packageHash(), actor,
                        new ChangePackageApprovalContext(principal.scope(),
                                AdminAuthService.SCOPE_ADMIN.equals(principal.scope()),
                                "Approved from Channel command surface")));
            } else {
                result = approval.reject(new ChangePackageCommands.Reject(
                        action.packageId(),
                        new ChangePackageRejectionRequest("Rejected from Channel command surface", ""),
                        actor));
            }
        } catch (RuntimeException failure) {
            if (actorClaimed) tokens.releaseActorClaim(action, actor);
            auditFailure(action, actor, failure);
            throw failure;
        }

        if (result.status() != ChangePackageStatus.REVIEWING) {
            try {
                tokens.complete(action, actor);
            } catch (RuntimeException ledgerFailure) {
                auditFailure(action, actor, ledgerFailure);
            }
        }
        auditSuccess(action, actor, result);
        return new ActionOutcome(true, action.decision(), result.status().name(), result.version(), result.packageHash(),
                result.requiredApprovals(), result.approvedCount());
    }

    private void assertFrozenAction(ChannelApprovalActionRecord action, ChangePackageCurrent current) {
        if (!action.projectId().equals(current.projectId())) throw new SecurityException("CHANNEL_APPROVAL_PROJECT_MISMATCH");
        if (current.status() != ChangePackageStatus.REVIEWING) throw new SecurityException("CHANNEL_APPROVAL_ACTION_STALE_STATE");
        if (current.version() != action.packageVersion()) throw new SecurityException("CHANNEL_APPROVAL_ACTION_STALE_VERSION");
        if (!current.packageHash().equals(action.packageHash())) throw new SecurityException("CHANNEL_APPROVAL_ACTION_STALE_HASH");
    }

    private ChannelIdentityRecord trustedIdentity(ChannelApprovalActionRecord action, String externalPrincipalId) {
        ChannelIdentityRecord identity = channels.findIdentity(action.channelId(), requiredText(externalPrincipalId, "CHANNEL_EXTERNAL_IDENTITY_REQUIRED"))
                .filter(item -> item.status() == ChannelStatus.ACTIVE)
                .orElseThrow(() -> new SecurityException("CHANNEL_APPROVAL_IDENTITY_MAPPING_REQUIRED"));
        if (!identities.isTrusted(action.projectId(), identity)) {
            throw new SecurityException("CHANNEL_APPROVAL_IDENTITY_NOT_AUTHORIZED");
        }
        return identity;
    }

    private AdminAuthService.AuthPrincipal principal(ChannelIdentityRecord identity) {
        AdminUserAccount user = users.findByUserId(identity.platformUserId());
        if (user == null || !Integer.valueOf(1).equals(user.status())
                || !identity.platformUserId().equals(user.userId()) || !identity.username().equals(user.username())) {
            throw new SecurityException("CHANNEL_APPROVAL_PLATFORM_USER_INVALID");
        }
        String scope = user.role() == AdminUserRole.ADMIN ? AdminAuthService.SCOPE_ADMIN : AdminAuthService.SCOPE_USER;
        return new AdminAuthService.AuthPrincipal(user.username(), user.userId(),
                "channel:" + identity.mappingId(), scope, false);
    }

    private ChannelRecord activeChannel(String channelId) {
        ChannelRecord channel = channels.findById(requiredText(channelId, "CHANNEL_ID_REQUIRED"))
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
        if (channel.status() != ChannelStatus.ACTIVE) throw new SecurityException("CHANNEL_DISABLED");
        return channel;
    }

    private ChangePackageCurrent current(String packageId) {
        if (!packages.available()) throw new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE");
        return packages.find(requiredText(packageId, "CHANGE_PACKAGE_ID_REQUIRED"))
                .orElseThrow(() -> new IllegalArgumentException("CHANGE_PACKAGE_NOT_FOUND"));
    }

    private void auditSuccess(ChannelApprovalActionRecord action,
                              String actor,
                              ChangePackageApprovalOutcome result) {
        try {
            audit.record(action.projectId(), action.packageId(), actor,
                    "CHANNEL_APPROVAL_ACTION_COMPLETED", action.actionId(), "MEDIUM", "SUCCEEDED",
                    Map.of("channelId", action.channelId(), "packageId", action.packageId(),
                            "packageVersion", action.packageVersion(), "decision", action.decision().name(),
                            "resultStatus", result.status().name(),
                            "requiredApprovals", result.requiredApprovals(),
                            "approvedCount", result.approvedCount()));
        } catch (RuntimeException ignored) {
            // ChangePackage approval/rejection already owns the authoritative audit trail.
        }
    }

    private void auditFailure(ChannelApprovalActionRecord action, String actor, RuntimeException failure) {
        try {
            audit.record(action.projectId(), action.packageId(), safe(actor),
                    "CHANNEL_APPROVAL_ACTION_REJECTED", action.actionId(), "MEDIUM", "BLOCKED",
                    Map.of("channelId", action.channelId(), "packageId", action.packageId(),
                            "packageVersion", action.packageVersion(), "decision", action.decision().name(),
                            "reasonCode", safe(failure.getMessage())));
        } catch (RuntimeException ignored) {
            // Denial remains authoritative even if secondary audit storage is unavailable.
        }
    }

    private String requiredText(String value, String reasonCode) {
        String normalized = safe(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> T required(T value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value;
    }

    public record ApprovalActions(String packageId,
                                  int packageVersion,
                                  String packageHash,
                                  List<ChannelInteractiveAction> actions) {
        public ApprovalActions {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record ActionOutcome(boolean handled,
                                ChannelApprovalActionRecord.Decision decision,
                                String status,
                                int version,
                                String packageHash,
                                int requiredApprovals,
                                int approvedCount) {
        public boolean waitingForMoreApprovals() {
            return decision == ChannelApprovalActionRecord.Decision.APPROVE
                    && ChangePackageStatus.REVIEWING.name().equals(status)
                    && approvedCount < requiredApprovals;
        }
    }
}
