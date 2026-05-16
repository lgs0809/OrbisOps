package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;
import cn.lgs.orbisops.domain.runtime.tool.service.ToolExposurePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolExposurePolicyTest {

    private final ToolExposurePolicy policy = new ToolExposurePolicy();
    private final ToolExposureSettings settings = ToolExposureSettings.defaults();

    @Test
    void explicitCapabilityIsDiscoveryInputNotTheFinalRuntimeAuthority() {
        assertTrue(policy.allows("mutating", settings));
        assertTrue(policy.allows("read_only", settings));
        assertTrue(policy.allows("notification", settings));
    }

    @Test
    void supportedReadWriteAndNotificationAliasesAreAllowed() {
        List.of(
                "read_only", "readonly", "read", "query", "search", "list", "get",
                "evidence", "observe", "inspect",
                "notification", "notify", "notice", "message", "push_report",
                "write", "mutating", "mutate", "update", "create", "delete", "insert",
                "execute", "apply", "deploy", "patch", "restart", "config", "configure", "ddl")
                .forEach(capability -> assertTrue(policy.allows(capability, settings), capability));
    }

    @Test
    void missingAndUnknownCapabilitiesFailClosed() {
        assertFalse(policy.allows(null, settings));
        assertFalse(policy.allows(" ", settings));
        assertFalse(policy.allows("custom-safe-ish", settings));
    }

    @Test
    void disabledExplicitCapabilityEnforcementAllowsTool() {
        ToolExposureSettings relaxed = ToolExposureSettings.fromRaw(
                "runtime-governed",
                false);

        assertTrue(policy.allows("custom-provider-capability", relaxed));
    }
}
