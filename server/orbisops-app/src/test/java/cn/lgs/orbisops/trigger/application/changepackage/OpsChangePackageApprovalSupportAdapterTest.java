package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillEvolutionSignalService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillRuntimeUsageRecorder;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsChangePackageApprovalSupportAdapterTest {

    @Test
    void createAuditAcceptsMissingBeforeAggregate() {
        ObjectProvider<OpsRepairWorkspaceService> repair = provider();
        ObjectProvider<OpsTrustedProofService> proof = provider();
        ObjectProvider<OpsConfigAuditService> audit = provider();
        ObjectProvider<OpsSkillEvolutionSignalService> signal = provider();
        ObjectProvider<OpsSkillRuntimeUsageRecorder> usage = provider();
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        when(audit.getIfAvailable()).thenReturn(auditService);
        OpsChangePackageApprovalSupportAdapter adapter =
                new OpsChangePackageApprovalSupportAdapter(repair, proof, audit, signal, usage);
        Map<String, Object> after = Map.of("packageId", "cp-1", "status", "DRAFT");

        adapter.record("project-1", "create", "cp-1", null, after);

        verify(auditService).record(
                eq("project-1"), eq("change-package"), eq("create"), eq("cp-1"), isNull(), eq(after));
    }

    @Test
    void constructorMustNotEagerlyResolveOptionalCollaborators() {
        ObjectProvider<OpsRepairWorkspaceService> repair = provider();
        ObjectProvider<OpsTrustedProofService> proof = provider();
        ObjectProvider<OpsConfigAuditService> audit = provider();
        ObjectProvider<OpsSkillEvolutionSignalService> signal = provider();
        ObjectProvider<OpsSkillRuntimeUsageRecorder> usage = provider();

        new OpsChangePackageApprovalSupportAdapter(repair, proof, audit, signal, usage);

        verifyNoInteractions(repair, proof, audit, signal, usage);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider() {
        return mock(ObjectProvider.class);
    }
}
