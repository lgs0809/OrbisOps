package cn.lgs.orbisops.domain.agent.adapter.repository;

import java.time.Instant;

public interface IJwtRevocationRepository {

    boolean available();

    boolean isRevoked(String jwtId);

    void revoke(String jwtId, String subject, Instant expiresAt);
}
