package cn.lgs.orbisops.application.incident;

import java.util.List;
import java.util.Map;

public record CreateIncidentCommand(
        String projectId,
        String title,
        String status,
        String severity,
        String serviceName,
        String sourceType,
        String summary,
        Map<String, Object> labels,
        Map<String, Object> metadata,
        List<String> affectedResources) {

    public CreateIncidentCommand {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        affectedResources = affectedResources == null ? List.of() : List.copyOf(affectedResources);
    }
}
