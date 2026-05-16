package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessSnapshot;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component("opsSafetyReadiness")
public class OpsSafetyReadinessHealthIndicator implements HealthIndicator {

    private final OpsToolResultStore toolResultStore;
    private final OpsTrustedProofService trustedProofService;
    private final OpsConfigAuditService auditService;
    private final ChangePackageReadinessPort changePackageReadiness;

    public OpsSafetyReadinessHealthIndicator(ObjectProvider<OpsToolResultStore> toolResultStoreProvider,
                                             ObjectProvider<OpsTrustedProofService> trustedProofServiceProvider,
                                             ObjectProvider<OpsConfigAuditService> auditServiceProvider,
                                             ObjectProvider<ChangePackageReadinessPort> changePackageReadinessProvider) {
        this.toolResultStore = toolResultStoreProvider.getIfAvailable();
        this.trustedProofService = trustedProofServiceProvider.getIfAvailable();
        this.auditService = auditServiceProvider.getIfAvailable();
        this.changePackageReadiness = changePackageReadinessProvider.getIfAvailable();
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean up = true;
        up &= check("toolResultStore", toolResultStore, details);
        up &= check("trustedProofStore", trustedProofService, details);
        up &= check("auditStore", auditService, details);
        up &= check("changePackageStore", changePackageReadiness, details);
        Health.Builder builder = up ? Health.up() : Health.down();
        return builder.withDetails(details).build();
    }

    private boolean check(String name, Object service, Map<String, Object> details) {
        if (service == null) {
            details.put(name, down("service bean missing"));
            return false;
        }
        try {
            if (service instanceof OpsToolResultStore store) {
                return recordReadiness(name, store.readiness(), details);
            } else if (service instanceof OpsTrustedProofService store) {
                return recordReadiness(name, store.readiness(), details);
            } else if (service instanceof OpsConfigAuditService store) {
                return recordReadiness(name, store.readiness(), details);
            } else if (service instanceof ChangePackageReadinessPort readiness) {
                ChangePackageReadinessSnapshot snapshot = readiness.readiness();
                if (snapshot == null) {
                    details.put(name, down("readiness snapshot missing"));
                    return false;
                }
                details.put(name, snapshot.details());
                return snapshot.up();
            } else {
                details.put(name, down("unsupported readiness service"));
                return false;
            }
        } catch (Exception e) {
            details.put(name, down(errorMessage(e)));
            return false;
        }
    }

    private boolean recordReadiness(
            String name,
            Map<String, Object> readiness,
            Map<String, Object> details) {
        if (readiness == null) {
            details.put(name, down("readiness result missing"));
            return false;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        readiness.forEach((key, value) -> {
            if (key != null) safe.put(key, value == null ? "" : value);
        });
        String status = text(safe.get("status"));
        boolean up = "UP".equalsIgnoreCase(status);
        if (status.isBlank()) {
            safe.put("status", "DOWN");
            safe.putIfAbsent("reason", "readiness status missing");
        } else if (!up) {
            safe.putIfAbsent("reason", "readiness reported " + status);
        }
        details.put(name, Map.copyOf(safe));
        return up;
    }

    private Map<String, Object> down(String reason) {
        return Map.of("status", "DOWN", "reason", reason);
    }

    private String errorMessage(Exception error) {
        String message = error == null ? "" : text(error.getMessage());
        return message.isBlank()
                ? (error == null ? "readiness check failed" : error.getClass().getSimpleName())
                : message;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
