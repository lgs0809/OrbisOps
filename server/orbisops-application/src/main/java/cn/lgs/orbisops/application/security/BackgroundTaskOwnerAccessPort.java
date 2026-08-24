package cn.lgs.orbisops.application.security;

/** Background work must inherit its original owner's current scope and status. */
@FunctionalInterface
public interface BackgroundTaskOwnerAccessPort {
    void requireAccess(String projectId,String ownerId);
}
