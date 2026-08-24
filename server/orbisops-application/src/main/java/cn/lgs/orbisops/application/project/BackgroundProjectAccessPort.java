package cn.lgs.orbisops.application.project;

/** Narrow project authorization boundary shared with background owner checks. */
@FunctionalInterface
public interface BackgroundProjectAccessPort {
    void requireAccess(String projectId,String username,String userId,boolean platformAdmin);
}
