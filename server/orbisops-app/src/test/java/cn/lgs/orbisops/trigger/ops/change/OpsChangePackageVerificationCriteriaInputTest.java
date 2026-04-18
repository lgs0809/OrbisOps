package cn.lgs.orbisops.trigger.ops.change;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OpsChangePackageVerificationCriteriaInputTest {
    @Test void carriesStandardsIntoApprovalDraftAndFreezesTopLevelFields() {
        var input = input();
        var request = create(input);
        assertEquals("s", request.get("serviceId"));
        var criteria = (List<?>) request.get("verificationCriteria");
        assertEquals(input.getVerificationCriteria(), criteria);
        input.getVerificationCriteria().get(0).put("maxErrorRate", .9);
        assertEquals(.01, ((Map<?,?>) criteria.get(0)).get("maxErrorRate"));
    }
    @Test void refusesRelaxedOrMissingRecoveryTargets() {
        var input = input();
        input.getVerificationCriteria().get(0).put("maxErrorRate", .02);
        assertThrows(IllegalArgumentException.class, () -> create(input));
        input.getVerificationCriteria().get(0).remove("maxErrorRate");
        assertThrows(IllegalArgumentException.class, () -> create(input));
    }
    @Test void refusesDifferentServiceResourceOrLoadDefinition() {
        for (String key : List.of("serviceId", "resourceIdentity", "environment", "minQps")) {
            var input = input();
            input.getVerificationCriteria().get(0).remove(key);
            assertThrows(IllegalArgumentException.class, () -> create(input));
        }
        var input = input(); input.getVerificationCriteria().get(0).put("resourceIdentity", "another-resource");
        assertThrows(IllegalArgumentException.class, () -> create(input));
    }
    @Test void rejectsDuplicateCriteriaAndDoesNotInventThemForLegacyInput() {
        var input = input(); input.getVerificationCriteria().add(new LinkedHashMap<>(input.getVerificationCriteria().get(0)));
        assertThrows(IllegalArgumentException.class, () -> create(input));
        input.setVerificationCriteria(null);
        assertFalse(create(input).containsKey("verificationCriteria"));
    }
    private OpsChangePackageToolInput input() {
        var input = new OpsChangePackageToolInput(); input.setServiceId("s");
        var c = new LinkedHashMap<String,Object>();
        c.put("kind", "OBSERVABILITY_SLO_V1"); c.put("serviceId", "s"); c.put("environment", "prod");
        c.put("expectedVersion", "v2"); c.put("baselineVersion", "v1"); c.put("resourceIdentity", "resource");
        c.put("routeDefinition", "orders-v1"); c.put("collectionDefinition", "metrics-v1"); c.put("changeKind", "RECOVERY");
        c.put("maxErrorRate", .01); c.put("maxP95Seconds", 1.); c.put("minQps", .5); c.put("maxQps", 2.);
        input.setVerificationCriteria(new ArrayList<>(List.of(c)));
        input.setActions(List.of(Map.of("writesTargetResource", true, "effectType", "MUTATE_TARGET_RESOURCE",
                "effectScope", "PRODUCTION", "targetEnvironment", "prod", "resourceScope", "resource")));
        return input;
    }
    private Map<String,Object> create(OpsChangePackageToolInput input) {
        var evidence = new OpsChangePackageRunEvidenceCollector.Evidence("e", "MCP", "run:read", "summary", "2026-09-10", "sha256:hash", Map.of());
        return new OpsChangePackageToolRequestFactory().create(new OpsChangePackageToolRequestFactory.Context("p", "run", "bundle", "hash"), input, List.of(evidence));
    }
}
