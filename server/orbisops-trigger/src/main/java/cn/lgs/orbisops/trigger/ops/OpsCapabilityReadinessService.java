package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.capability.CapabilityDependencyReadiness;
import cn.lgs.orbisops.application.capability.CapabilityReadinessSnapshot;
import cn.lgs.orbisops.application.capability.CapabilityReadinessState;
import cn.lgs.orbisops.application.capability.CapabilityReadinessUseCase;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP-facing facade for the typed capability readiness Application service. */
@Service
public class OpsCapabilityReadinessService {

    private final CapabilityReadinessUseCase readinessUseCase;

    public OpsCapabilityReadinessService(CapabilityReadinessUseCase readinessUseCase) {
        if (readinessUseCase == null) {
            throw new IllegalArgumentException("CAPABILITY_READINESS_USE_CASE_REQUIRED");
        }
        this.readinessUseCase = readinessUseCase;
    }

    public Map<String, Object> snapshot() {
        CapabilityReadinessSnapshot snapshot = readinessUseCase.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", snapshot.generatedAt().toString());
        result.put("analysisReady", capability(snapshot.analysisReady()));
        result.put("changePackageReady", capability(snapshot.changePackageReady()));
        result.put("approvedLandingReady", capability(snapshot.approvedLandingReady()));
        Map<String, Map<String, Object>> dependencies = new LinkedHashMap<>();
        for (CapabilityDependencyReadiness dependency : snapshot.dependencies()) {
            dependencies.put(dependency.name(), dependency.attributes());
        }
        result.put("dependencies", dependencies);
        return result;
    }

    private Map<String, Object> capability(CapabilityReadinessState state) {
        return Map.of(
                "ready", state.ready(),
                "status", state.status(),
                "reasonCodes", state.reasonCodes());
    }
}
