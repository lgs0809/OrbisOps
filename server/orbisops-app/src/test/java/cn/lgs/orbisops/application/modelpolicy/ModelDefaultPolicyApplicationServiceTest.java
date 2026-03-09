package cn.lgs.orbisops.application.modelpolicy;

import cn.lgs.orbisops.domain.modelpolicy.model.ModelDefaultPolicy;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelPolicyStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDefaultPolicyApplicationServiceTest {

    @Test
    void updateAuditsAuthenticatedActor() {
        ModelDefaultPolicyPort policyPort = mock(ModelDefaultPolicyPort.class);
        ModelCatalogPort catalogPort = mock(ModelCatalogPort.class);
        ModelPolicyAuditPort auditPort = mock(ModelPolicyAuditPort.class);
        ModelDefaultPolicyApplicationService service = new ModelDefaultPolicyApplicationService(
                policyPort, catalogPort, auditPort);
        ModelDefaultPolicySnapshot before = snapshot(1L, "project-1");
        ModelDefaultPolicySnapshot after = snapshot(1L, "project-1");
        when(policyPort.find("project-1")).thenReturn(Optional.of(before));
        when(policyPort.save(any(ModelDefaultPolicy.class))).thenReturn(after);

        service.update("project-1", Map.of("status", "ENABLED"), "alice");

        ArgumentCaptor<Object> auditAfter = ArgumentCaptor.forClass(Object.class);
        verify(auditPort).record(
                eq("project-1"),
                eq(before.view()),
                auditAfter.capture());
        assertEquals("alice", ((Map<?, ?>) auditAfter.getValue()).get("actor"));
        assertEquals(after.view(), ((Map<?, ?>) auditAfter.getValue()).get("policy"));
    }

    @Test
    void updateRejectsMissingAuthenticatedActorBeforePersistence() {
        ModelDefaultPolicyPort policyPort = mock(ModelDefaultPolicyPort.class);
        ModelCatalogPort catalogPort = mock(ModelCatalogPort.class);
        ModelPolicyAuditPort auditPort = mock(ModelPolicyAuditPort.class);
        ModelDefaultPolicyApplicationService service = new ModelDefaultPolicyApplicationService(
                policyPort, catalogPort, auditPort);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.update("project-1", Map.of("status", "ENABLED"), " "));

        assertEquals("MODEL_POLICY_ACTOR_REQUIRED", error.getMessage());
    }

    private ModelDefaultPolicySnapshot snapshot(long id, String projectId) {
        return new ModelDefaultPolicySnapshot(
                id,
                new ModelDefaultPolicy(projectId, "", "", "", "", ModelPolicyStatus.ENABLED),
                null,
                null);
    }
}
