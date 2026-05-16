package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.resourcehealth.ResourceCapability;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthApplicationService;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthSnapshot;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Legacy Map presenter over the typed Resource Health application query. */
@Service
public class OpsResourceHealthService {

    private final ResourceHealthApplicationService applicationService;

    public OpsResourceHealthService(
            ResourceHealthApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public Map<String, Object> snapshot() {
        ResourceHealthSnapshot snapshot = applicationService.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", snapshot.generatedAt());
        result.put(
                "checks",
                snapshot.checks().stream()
                        .map(this::checkView)
                        .toList());
        result.put("healthyCount", snapshot.healthyCount());
        result.put("degradedCount", snapshot.degradedCount());
        return result;
    }

    public List<Map<String, Object>> capabilities() {
        return applicationService.capabilities().stream()
                .map(this::capabilityView)
                .toList();
    }

    private Map<String, Object> checkView(ResourceHealthCheck check) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", check.id());
        row.put("name", check.name());
        row.put("endpoint", check.endpoint());
        row.put("healthy", check.healthy());
        row.put("message", check.message());
        row.putAll(check.details());
        return row;
    }

    private Map<String, Object> capabilityView(ResourceCapability capability) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", capability.id());
        row.put("name", capability.name());
        row.put("accessMode", capability.accessMode());
        row.put("dataShape", capability.dataShape());
        return row;
    }
}
