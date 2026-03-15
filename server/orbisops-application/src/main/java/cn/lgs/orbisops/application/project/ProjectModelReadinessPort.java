package cn.lgs.orbisops.application.project;

/** Supplies whether at least one usable Chat model and Provider are configured. */
@FunctionalInterface
public interface ProjectModelReadinessPort {

    boolean available();
}
