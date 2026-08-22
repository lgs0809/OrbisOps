package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.application.security.JwtRevocationPort;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shared admin authentication service for JWT, service tokens, password hashing,
 * and token revocation.
 */
@Slf4j
@Service
public class AdminAuthService {

    private static final String ISSUER = "orbisops";
    public static final String SCOPE_ADMIN = "admin";
    public static final String SCOPE_USER = "user";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    private final AdminAuthSettings settings;

    private final JwtRevocationPort jwtRevocationRepository;

    @Autowired
    public AdminAuthService(
            JwtRevocationPort jwtRevocationRepository,
            AdminAuthSettings settings) {
        this.jwtRevocationRepository = jwtRevocationRepository;
        this.settings = settings == null
                ? AdminAuthSettings.defaults()
                : settings;
    }

    @PostConstruct
    public void validateJwtSecret() {
        if (!settings.rejectWeakSecrets()) {
            return;
        }
        String jwtSecret = settings.jwtSecret();
        if (!StringUtils.hasText(jwtSecret)
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("orbisops.admin.auth.jwt-secret must be configured with at least 32 bytes");
        }
    }

    public String issueToken(AdminUserAccount adminUser) {
        Instant now = Instant.now();
        String scope = normalizeScope(adminUser.role() == null ? null : adminUser.role().value());
        return JWT.create()
                .withIssuer(ISSUER)
                .withJWTId(UUID.randomUUID().toString())
                .withSubject(adminUser.username())
                .withClaim("userId", adminUser.userId())
                .withClaim("scope", scope)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(settings.jwtTtlHours() * 3600)))
                .sign(Algorithm.HMAC256(settings.jwtSecret()));
    }

    public Optional<AuthPrincipal> verifyAuthorization(String authorization) {
        return verifyAuthorization(authorization, SCOPE_ADMIN);
    }

    public Optional<AuthPrincipal> verifyAuthorization(String authorization, String... acceptedScopes) {
        String token = bearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        Set<String> scopes = acceptedScopeSet(acceptedScopes);
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(settings.jwtSecret()))
                    .withIssuer(ISSUER)
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            String scope = normalizeScope(jwt.getClaim("scope").asString());
            if (!scopes.contains(scope) || !StringUtils.hasText(jwt.getSubject())) {
                return Optional.empty();
            }
            if (isRevoked(jwt)) {
                return Optional.empty();
            }
            return Optional.of(new AuthPrincipal(jwt.getSubject(), jwt.getClaim("userId").asString(), jwt.getId(), scope, false));
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }

    public boolean verifyServiceToken(String token) {
        return settings.matchesServiceCredential(token);
    }

    public boolean revokeAuthorization(String authorization) {
        String token = bearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            DecodedJWT jwt = JWT.decode(token);
            if (!StringUtils.hasText(jwt.getId())) {
                return false;
            }
            persistRevokedToken(jwt);
            return true;
        } catch (Exception e) {
            log.warn("注销 JWT 失败：{}", e.getMessage());
            return false;
        }
    }

    public boolean passwordMatches(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(storedPassword)) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    public String hashPasswordForStorage(String password) {
        if (!StringUtils.hasText(password)) {
            return password;
        }
        return isBcrypt(password) ? password : passwordEncoder.encode(password);
    }

    public boolean isBcrypt(String password) {
        return StringUtils.hasText(password)
                && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    private String bearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private Set<String> acceptedScopeSet(String... acceptedScopes) {
        if (acceptedScopes == null || acceptedScopes.length == 0) {
            return Set.of(SCOPE_ADMIN);
        }
        return Arrays.stream(acceptedScopes)
                .map(this::normalizeScope)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return SCOPE_USER;
        }
        String normalized = scope.trim().toLowerCase();
        if (SCOPE_USER.equals(normalized) || SCOPE_ADMIN.equals(normalized)) {
            return normalized;
        }
        return normalized;
    }

    private boolean isRevoked(DecodedJWT jwt) {
        if (!StringUtils.hasText(jwt.getId()) || jwtRevocationRepository == null || !jwtRevocationRepository.available()) {
            return false;
        }
        return jwtRevocationRepository.isRevoked(jwt.getId());
    }

    private void persistRevokedToken(DecodedJWT jwt) {
        if (jwtRevocationRepository == null || !jwtRevocationRepository.available()) {
            return;
        }
        Instant expiresAt = jwt.getExpiresAt() == null ? Instant.now().plusSeconds(3600) : jwt.getExpiresAt().toInstant();
        jwtRevocationRepository.revoke(jwt.getId(), jwt.getSubject(), expiresAt);
    }

    public record AuthPrincipal(String username, String userId, String jwtId, String scope, boolean serviceToken) {
    }
}
