package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Locale;

/** Stable actor facts used by the approval policy. */
public record ChangePackageApprovalActorContext(
        String actorScope,
        boolean adminConfirmation) {

    public ChangePackageApprovalActorContext {
        actorScope = actorScope == null ? "" : actorScope.trim().toLowerCase(Locale.ROOT);
    }

    public static ChangePackageApprovalActorContext anonymous() {
        return new ChangePackageApprovalActorContext("", false);
    }
}
