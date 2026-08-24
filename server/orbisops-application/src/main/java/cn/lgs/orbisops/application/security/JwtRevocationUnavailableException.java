package cn.lgs.orbisops.application.security;

/** Safe boundary error: underlying driver text and identities must never escape authentication. */
public final class JwtRevocationUnavailableException extends RuntimeException {
    public JwtRevocationUnavailableException() {
        super("JWT_REVOCATION_UNAVAILABLE");
    }
}
