package cn.lgs.orbisops.application.security;

import java.time.Instant;

/** Persistence boundary for JWT revocation state used by the authentication adapter. */
public interface JwtRevocationPort {

    boolean available();

    boolean isRevoked(String jwtId);

    void revoke(String jwtId, String subject, Instant expiresAt);
}
