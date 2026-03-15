package cn.lgs.orbisops.application.project;

import java.util.List;
import java.util.Map;

public interface ProjectMcpUpdatePreparationPort {

    Map<String, Object> enrichPermission(
            String resourceType,
            Map<String, Object> permission);

    Map<String, Object> enrichTransportMetadata(
            String resourceType,
            List<String> allowedActions,
            Map<String, Object> transportConfig);
}
