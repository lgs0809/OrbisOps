package cn.lgs.orbisops.application.security;

/** Atomic credential replacement. Version compares protect password change/reset races. */
public interface AdminCredentialLifecyclePort {
    boolean changeCredential(String userId, long expectedVersion, String encodedCredential);
    boolean resetToInitialCredential(String userId, long expectedVersion);
}
