package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsProjectResourceCredentialPolicyTest {

    @Test
    void preparesAndResolvesSecretReferenceWithoutPersistingPlaintext() {
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        when(secretResolver.isReference("${env:TEST_DB_PASSWORD}")).thenReturn(true);
        when(secretResolver.resolve("${env:TEST_DB_PASSWORD}")).thenReturn("resolved-password");
        OpsProjectResourceCredentialPolicy policy =
                new OpsProjectResourceCredentialPolicy(secretResolver);

        Map<String, Object> credential = policy.prepare(
                Map.of("username", "reader", "password", "${env:TEST_DB_PASSWORD}"),
                "mysql",
                false,
                Map.of());
        Map<String, Object> resolved = policy.resolve(credential);

        assertEquals("reader", credential.get("username"));
        assertEquals("${env:TEST_DB_PASSWORD}", credential.get("passwordRef"));
        assertTrue(Boolean.TRUE.equals(credential.get("configured")));
        assertEquals("resolved-password", resolved.get("password"));
    }

    @Test
    void rejectsPlaintextPassword() {
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        OpsProjectResourceCredentialPolicy policy =
                new OpsProjectResourceCredentialPolicy(secretResolver);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> policy.prepare(
                        Map.of("password", "plaintext-password"),
                        "mysql",
                        false,
                        Map.of()));

        assertTrue(error.getMessage().contains("不能保存明文"));
    }

    @Test
    void normalizesLegacyReferenceAndPreservesItDuringPartialUpdate() {
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        when(secretResolver.isReference("${env:LEGACY_DB_PASSWORD}")).thenReturn(true);
        OpsProjectResourceCredentialPolicy policy =
                new OpsProjectResourceCredentialPolicy(secretResolver);

        Map<String, Object> normalized = policy.normalizeStored(Map.of(
                "username", "legacy-reader",
                "password", "${env:LEGACY_DB_PASSWORD}"));
        Map<String, Object> updated = policy.prepare(
                Map.of("username", "next-reader"),
                "postgresql",
                true,
                normalized);

        assertEquals("${env:LEGACY_DB_PASSWORD}", normalized.get("passwordRef"));
        assertEquals("next-reader", updated.get("username"));
        assertEquals("${env:LEGACY_DB_PASSWORD}", updated.get("passwordRef"));
    }
}
