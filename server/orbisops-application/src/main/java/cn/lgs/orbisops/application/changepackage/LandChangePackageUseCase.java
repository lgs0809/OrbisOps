package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

/** Public application API for landing and cleanup. */
public final class LandChangePackageUseCase {

    private final ChangePackageLandingProcessManager landingProcessManager;
    private final ChangePackageCleanupUseCase cleanupUseCase;
    private final ChangePackageQueryPort queryPort;

    public LandChangePackageUseCase(ChangePackageLandingProcessManager landingProcessManager,
                                    ChangePackageCleanupUseCase cleanupUseCase,
                                    ChangePackageQueryPort queryPort) {
        if (landingProcessManager == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_PROCESS_MANAGER_REQUIRED");
        }
        if (cleanupUseCase == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CLEANUP_USE_CASE_REQUIRED");
        if (queryPort == null) throw new IllegalArgumentException("CHANGE_PACKAGE_QUERY_PORT_REQUIRED");
        this.landingProcessManager = landingProcessManager;
        this.cleanupUseCase = cleanupUseCase;
        this.queryPort = queryPort;
    }

    public Map<String, Object> land(ChangePackageCommands.Land command) {
        landingProcessManager.land(command);
        return queryPort.detail(command.packageId());
    }

    public Map<String, Object> cleanup(ChangePackageCommands.Cleanup command) {
        cleanupUseCase.cleanup(command);
        return queryPort.detail(command.packageId());
    }

    public Map<String, Object> verifyLanding(String packageId, String actor) {
        return landingProcessManager.verifyLanding(packageId, actor);
    }
}
