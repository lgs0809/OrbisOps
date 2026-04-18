package cn.lgs.orbisops.application.changepackage;

/** Operational lease settings for Landing dispatch and reconciliation. */
public record LandingOperationJournalSettings(int leaseSeconds) {

    public LandingOperationJournalSettings {
        leaseSeconds = Math.max(30, leaseSeconds);
    }
}
