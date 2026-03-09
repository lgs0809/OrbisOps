package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.modelpolicy.ModelCatalogPort;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyPort;
import cn.lgs.orbisops.application.modelpolicy.ModelPolicyAuditPort;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelUsage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class OpsModelDefaultPolicyServiceTest {

    @Test
    void shouldRejectDefaultModelWithWrongUsageBeforePersistence() {
        ModelDefaultPolicyPort policyPort = mock(ModelDefaultPolicyPort.class);
        ModelCatalogPort catalogPort = mock(ModelCatalogPort.class);
        ModelPolicyAuditPort auditPort = mock(ModelPolicyAuditPort.class);
        ModelDefaultPolicyApplicationService service = new ModelDefaultPolicyApplicationService(
                policyPort,
                catalogPort,
                auditPort);
        doThrow(new IllegalArgumentException("MODEL_POLICY_USAGE_MISMATCH:embedding-only:CHAT"))
                .when(catalogPort)
                .requireAvailable(eq("embedding-only"), eq(ModelUsage.CHAT));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(null, Map.of("defaultChatModelId", "embedding-only"), "alice"));

        assertTrue(error.getMessage().contains("CHAT"));
    }
}
