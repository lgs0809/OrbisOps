package cn.lgs.orbisops.application.changepackage;

/** Operational readiness boundary; not part of the ChangePackage domain model. */
public interface ChangePackageReadinessPort {

    ChangePackageReadinessSnapshot readiness();

    boolean approvedLandingEnabled();

    boolean operationJournalReady();
}
