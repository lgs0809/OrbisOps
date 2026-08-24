package cn.lgs.orbisops.application.security;

import java.time.Instant;

/** Persistence boundary for JWT revocation state used by the authentication adapter. */
public interface JwtRevocationPort {

    /** Whether durable revocation is enabled. An enabled but failing store must throw, never degrade silently. */
    boolean available();

    boolean isRevoked(String jwtId);

    void revoke(String jwtId, String subject, Instant expiresAt);
}
