package cn.lgs.orbisops.application.memory;

/** Secondary port for the cross-cutting configuration audit projection. */
@FunctionalInterface
public interface GovernedMemoryExternalAuditPort {

    void recordCreate(String projectId,
                      boolean conflict,
                      GovernedMemoryCreationResult result);
}
