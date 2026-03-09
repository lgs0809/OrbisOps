package cn.lgs.orbisops.domain.source.model;

import java.util.List;

public record SourceRepositoryCapabilities(
        boolean enabled,
        String mode,
        List<String> operations,
        boolean remoteCloneEnabled,
        boolean allowedLocalRootsConfigured) {

    public SourceRepositoryCapabilities {
        mode = mode == null ? "LOCAL_GIT_READ_ONLY" : mode.trim();
        operations = operations == null ? List.of() : List.copyOf(operations);
    }
}
