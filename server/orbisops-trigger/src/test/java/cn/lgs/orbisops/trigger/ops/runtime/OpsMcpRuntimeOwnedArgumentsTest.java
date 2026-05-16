package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpsMcpRuntimeOwnedArgumentsTest {
    @Test void actualDeclaredAuthorityFieldsPreserveBusinessObjectAndNeverInventActor() {
        var config = config();
        var configuration = Map.of("delayMs", 75, "failEvery", 0);
        var business = Map.<String, Object>of("configuration", configuration, "version", "candidate-v2");
        var declared = Map.<String, Object>of("properties", Map.of("projectId", Map.of(), "executionKey", Map.of(), "deadline", Map.of()));
        var projected = OpsMcpRuntimeOwnedArguments.project(config, declared, business);
        assertEquals(Map.of("configuration", configuration, "version", "candidate-v2", "projectId", "native-project",
                "executionKey", "native-key", "deadline", config.getAuthorityDeadline().toString()), projected);
        assertSame(configuration, projected.get("configuration"));
        assertFalse(projected.containsKey("actor"));
        assertEquals(2, business.size());
    }

    @Test void changedExplicitIdentityIsRejectedAndUndeclaredFieldsAreNotAdded() {
        var schema = Map.<String, Object>of("properties", Map.of("executionKey", Map.of()));
        assertThrows(SecurityException.class, () -> OpsMcpRuntimeOwnedArguments.project(config(), schema, Map.of("executionKey", "forged")));
        assertEquals(Map.of("service", "normal"), OpsMcpRuntimeOwnedArguments.project(config(), Map.of(), Map.of("service", "normal")));
    }

    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder().projectId("native-project").toolCallStage("LANDING").landingApproved(true)
                .internalCaller(OpsToolsetRouter.LANDING_INTERNAL_CALLER).landingRuntimeToken(OpsToolsetRouter.LANDING_RUNTIME_TOKEN)
                .changePackageId("native-package").approvedPackageHash("c".repeat(64)).approvedPackageVersion(1)
                .authorityDeadline(Instant.now().plusSeconds(30)).headers(Map.of("X-Ops-Execution-Key", "native-key")).build();
    }
}
