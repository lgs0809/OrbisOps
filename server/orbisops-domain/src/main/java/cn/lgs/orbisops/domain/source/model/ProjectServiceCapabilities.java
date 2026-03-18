package cn.lgs.orbisops.domain.source.model;

import java.util.List;

public record ProjectServiceCapabilities(
        boolean enabled,
        List<String> buildProfiles,
        boolean arbitraryShellAllowed,
        int serviceCount) {

    public ProjectServiceCapabilities {
        buildProfiles = buildProfiles == null ? List.of() : List.copyOf(buildProfiles);
        serviceCount = Math.max(0, serviceCount);
    }
}
