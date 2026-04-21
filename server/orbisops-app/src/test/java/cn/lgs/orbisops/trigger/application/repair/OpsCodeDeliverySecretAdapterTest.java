package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsCodeDeliverySecretAdapterTest {

    @Test
    void delegatesSecretReferenceResolution() {
        OpsSecretResolver resolver = mock(OpsSecretResolver.class);
        when(resolver.resolve("github-token")).thenReturn("secret");
        OpsCodeDeliverySecretAdapter adapter = new OpsCodeDeliverySecretAdapter(resolver);

        assertEquals("secret", adapter.resolve("github-token"));
        verify(resolver).resolve("github-token");
    }
}
