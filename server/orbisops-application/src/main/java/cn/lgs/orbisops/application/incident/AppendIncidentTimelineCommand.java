package cn.lgs.orbisops.application.incident;

import java.util.Map;

public record AppendIncidentTimelineCommand(
        String eventType,
        String title,
        String detail,
        String refType,
        String refId,
        Map<String, Object> payload) {

    public AppendIncidentTimelineCommand {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
