package cn.lgs.orbisops.domain.runtime.contextbundle.adapter.repository;

import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;

import java.util.Optional;

public interface IRuntimeContextBundleRepository {

    RuntimeContextBundleSnapshot save(RuntimeContextBundleSnapshot snapshot);

    Optional<RuntimeContextBundleSnapshot> find(String bundleId);

    Optional<RuntimeContextBundleSnapshot> latestForSession(String sessionId, String projectId);

    Optional<RuntimeContextBundleSnapshot> latestCompletedForSession(
            String sessionId,
            String projectId,
            String actor);
}
