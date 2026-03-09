package cn.lgs.orbisops.domain.evidence;

import cn.lgs.orbisops.domain.evidence.service.SensitiveDataRedactionPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataRedactionPolicyTest {

    private final SensitiveDataRedactionPolicy policy = new SensitiveDataRedactionPolicy();

    @Test
    void recursiveStructuresMustRedactKeysAndEmbeddedCredentials() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("password", "value-1");
        source.put("endpoint", "https://user:" + "value-2" + "@host/path?token=value-3");
        source.put("headers", Map.of(
                "Authorization", "Bearer " + "segment.value.signature",
                "normal", "ok"));
        source.put("items", List.of(Map.of("client_secret", "value-4")));

        Map<String, Object> redacted = policy.redactMap(source);
        String rendered = String.valueOf(redacted);

        assertEquals("***", redacted.get("password"));
        assertTrue(rendered.contains("https://***:***@host/path?token=***"));
        assertTrue(rendered.contains("Authorization=***"));
        assertTrue(rendered.contains("client_secret=***"));
        assertFalse(rendered.contains("value-1"));
        assertFalse(rendered.contains("value-2"));
        assertFalse(rendered.contains("value-3"));
        assertFalse(rendered.contains("value-4"));
    }

    @Test
    void cyclesDepthAndNullCollectionItemsMustRemainBounded() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);
        List<Object> values = new ArrayList<>();
        values.add(null);
        values.add(cyclic);

        Object redacted = policy.redact(values);

        assertTrue(String.valueOf(redacted).contains("<redacted:cycle>"));
        assertTrue(redacted instanceof List<?>);
        assertEquals(null, ((List<?>) redacted).get(0));

        Map<String, Object> deep = new LinkedHashMap<>();
        Map<String, Object> cursor = deep;
        for (int index = 0; index < 20; index++) {
            Map<String, Object> next = new LinkedHashMap<>();
            cursor.put("next", next);
            cursor = next;
        }
        assertTrue(String.valueOf(policy.redact(deep)).contains("<redacted:depth-limit>"));
    }

    @Test
    void textRedactionMustCoverBasicJwtAndQuerySecrets() {
        String source = "Basic dXNlcjpwYXNz "
                + "headerpart.payloadpart.signaturepart "
                + "jdbc://host/db?password=value&api_key=other";

        String redacted = policy.redactText(source);

        assertTrue(redacted.contains("Basic ***"));
        assertTrue(redacted.contains("***.***.***"));
        assertTrue(redacted.contains("password=***"));
        assertTrue(redacted.contains("api_key=***"));
        assertFalse(redacted.contains("dXNlcjpwYXNz"));
    }
}
