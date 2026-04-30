package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.service.TaskReceiptEvidencePolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskReceiptEvidencePolicyTest {
    final TaskReceiptEvidencePolicy policy = new TaskReceiptEvidencePolicy();
    Map<String,Object> envelope(Map<String,Object> content) {
        return Map.of("orbisopsResultVersion",1,"isError",false,"normalizedContent",content);
    }
    @Test void genericBusinessEvidenceDoesNotNeedObservabilityStatusAndScope() {
        var body=Map.<String,Object>of("version","v1","status","HEALTHY");
        assertEquals(body,policy.content("p",envelope(body)).orElseThrow());
        assertEquals(Map.of("environment","","service","","resource","mcp-tool:read_state"),policy.condition("read_state",body));
        assertEquals(policy.condition("read_state",body),policy.condition("read_state",Map.of("status","DOWN")));
    }
    @Test void legacyObservationAvailabilityAndProjectRestrictionsRemainEnforced() {
        var body=new LinkedHashMap<String,Object>(Map.of("status","AVAILABLE","scope",Map.of("projectId","p","environment","test","serviceId","orders"),"resourceIdentity","db"));
        assertTrue(policy.content("p",envelope(body)).isPresent());
        assertEquals(Map.of("environment","test","service","orders","resource","db"),policy.condition("read",body));
        body.put("status","UNAVAILABLE"); assertTrue(policy.content("p",envelope(body)).isEmpty());
        assertThrows(SecurityException.class,()->policy.content("other",envelope(body)));
        assertThrows(SecurityException.class,()->policy.content("p",envelope(Map.of("version","v1","scope",Map.of("projectId","foreign")))));
    }
    @Test void missingEnvelopeErrorResultsAndNoBusinessBodyAreNotEvidence() {
        assertTrue(policy.content("p",Map.of("version","v1")).isEmpty());
        assertTrue(policy.content("p",envelope(Map.of())).isEmpty());
        assertTrue(policy.content("p",Map.of("orbisopsResultVersion",1,"isError",true,"normalizedContent",Map.of("version","v1"))).isEmpty());
    }
    @Test void genericBusinessStatusRemainsCheckableWhenToolAlsoReportsItsProject() {
        var body=Map.<String,Object>of("status","HEALTHY","scope",Map.of("projectId","p"));
        assertEquals(body,policy.content("p",envelope(body)).orElseThrow());
        assertFalse(TaskReceiptEvidencePolicy.scopedObservation(body));
        assertFalse(new cn.lgs.orbisops.domain.skill.service.TaskAcceptancePolicy().isReceiptMetadata("/status",body));
        assertThrows(SecurityException.class,()->policy.content("other",envelope(body)));
    }
}
