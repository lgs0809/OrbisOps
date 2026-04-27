package cn.lgs.orbisops.application.channel;

import java.util.List;
import java.util.Map;

public final class ChannelIdentityService {

    private final ChannelCatalogQuery catalogQuery;
    private final ChannelManagementApplicationService managementService;

    public ChannelIdentityService(ChannelCatalogQuery catalogQuery,
                                  ChannelManagementApplicationService managementService) {
        if (catalogQuery == null) throw new IllegalArgumentException("CHANNEL_CATALOG_QUERY_REQUIRED");
        if (managementService == null) throw new IllegalArgumentException("CHANNEL_MANAGEMENT_SERVICE_REQUIRED");
        this.catalogQuery = catalogQuery;
        this.managementService = managementService;
    }

    public List<Map<String, Object>> list(String projectId, String channelId) {
        return catalogQuery.identities(required(projectId, "CHANNEL_PROJECT_ID_REQUIRED"),
                required(channelId, "CHANNEL_ID_REQUIRED"));
    }

    public Map<String, Object> bind(ChannelModels.IdentityBinding command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_IDENTITY_COMMAND_REQUIRED");
        return managementService.bindIdentity(command);
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
