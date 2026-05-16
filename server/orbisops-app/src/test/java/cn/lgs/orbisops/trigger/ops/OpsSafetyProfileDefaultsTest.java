package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSafetyProfileDefaultsTest {

    @Test
    void devAndTestProfilesDoNotEnableInMemoryEvidenceFallbackByDefault() throws IOException {
        assertFallbackDefault("application-dev.yml");
        assertFallbackDefault("application-test.yml");
    }

    private void assertFallbackDefault(String resource) throws IOException {
        String yaml = resource(resource);
        assertTrue(yaml.contains("allow-in-memory-evidence-store: ${ORBISOPS_SAFETY_ALLOW_IN_MEMORY_EVIDENCE_STORE:false}"),
                resource + " must require explicit opt-in for in-memory evidence fallback");
        assertFalse(yaml.contains("allow-in-memory-evidence-store: ${ORBISOPS_SAFETY_ALLOW_IN_MEMORY_EVIDENCE_STORE:true}"),
                resource + " must not default safety-critical stores to memory fallback");
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("resource not found: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
