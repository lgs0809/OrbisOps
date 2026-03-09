package cn.lgs.orbisops.application.worksession.run;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkSessionRunStartCommand(
        String runId,
        String projectId,
        String sessionId,
        String actor,
        String agentId,
        Integer agentVersion,
        String agentDefinitionHash,
        String executionHarness,
        int harnessVersion,
        String harnessHash,
        String engine,
        String adapterKey,
        String modelId,
        String modelProfileId,
        long modelProfileVersion,
        Map<String, Object> promptIdentity,
        Map<String, Object> requestIdentity,
        Map<String, Object> requestPayload,
        Map<String, Object> metadata) {

    public WorkSessionRunStartCommand {
        promptIdentity = immutable(promptIdentity);
        requestIdentity = immutable(requestIdentity);
        requestPayload = immutable(requestPayload);
        metadata = immutable(metadata);
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
