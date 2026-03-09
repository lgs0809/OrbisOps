package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeAuditPort;
import cn.lgs.orbisops.application.repair.ControlledCodeProofPort;
import cn.lgs.orbisops.application.repair.ControlledCodeToolResultPort;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeAction;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolOutputBudget;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsControlledCodeAdaptersTest {

    @Test
    void auditAdapterMapsTypedActionToRuntimeAuditContract() {
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        OpsControlledCodeAuditAdapter adapter = new OpsControlledCodeAuditAdapter(provider(audits));
        ControlledCodeAuditPort.ControlledCodeAuditEvent event =
                new ControlledCodeAuditPort.ControlledCodeAuditEvent(
                        "project-1", "alice", ControlledCodeAction.EDIT,
                        "repair-1:App.java", "MEDIUM", "SUCCESS", Map.of("afterHash", "hash"));

        adapter.record(event);

        verify(audits).recordRuntimeEvent(
                "project-1", "", "alice", "controlled-code-tool", "code.edit",
                "repair-1:App.java", "MEDIUM", "SUCCESS", event.payload());
    }

    @Test
    void toolResultAdapterPreservesResultReferencesAndBudget() {
        OpsToolResultStore store = mock(OpsToolResultStore.class);
        when(store.record(
                eq("project-1"), eq("session-1"), eq("run-1"), eq("alice"),
                eq("code.repair"), eq("code_bash"), eq("CONTROLLED_BASH_EXECUTED"),
                eq("mvn test"), eq("ok"), any(OpsToolOutputBudget.class), eq("alice")))
                .thenReturn(Map.of(
                        "resultId", "tool-result-1",
                        "truncated", false,
                        "fullOutputRef", "db:tool-result-1",
                        "outputHash", "output-hash"));
        OpsControlledCodeToolResultAdapter adapter =
                new OpsControlledCodeToolResultAdapter(provider(store));

        ControlledCodeToolResultPort.StoredToolResult result = adapter.record(
                new ControlledCodeToolResultPort.ToolResultRequest(
                        "project-1", "session-1", "run-1", "alice", "code.repair",
                        "code_bash", "CONTROLLED_BASH_EXECUTED", "mvn test", "ok",
                        32768, 400, "alice"));

        assertTrue(adapter.available());
        assertEquals("tool-result-1", result.resultId());
        assertFalse(result.truncated());
        assertEquals("db:tool-result-1", result.fullOutputRef());
        assertEquals("output-hash", result.outputHash());
    }

    @Test
    void proofAdapterMapsTypedProofToTrustedProofContract() {
        OpsTrustedProofService proofs = mock(OpsTrustedProofService.class);
        OpsControlledCodeProofAdapter adapter = new OpsControlledCodeProofAdapter(provider(proofs));
        ControlledCodeProofPort.ControlledCodeProof proof =
                new ControlledCodeProofPort.ControlledCodeProof(
                        "project-1", "repair-1", "cp-1", 2, "package-hash", "HIGH",
                        "tool-result-1", "command-hash", ControlledCodeEffect.TEST_OR_BUILD,
                        "tool-result-1", "db:tool-result-1", "output-hash", 0);

        adapter.record(proof, "alice");

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(proofs).recordTrustedProof(payload.capture(), eq("alice"));
        assertEquals("cp-1", payload.getValue().get("packageId"));
        assertEquals(2, payload.getValue().get("packageVersion"));
        assertEquals("CONTROLLED_BASH_EXECUTED", payload.getValue().get("source"));
        Map<?, ?> metadata = (Map<?, ?>) payload.getValue().get("metadata");
        assertEquals("TEST_OR_BUILD", metadata.get("expectedEffect"));
        assertEquals("tool-result-1", metadata.get("resultId"));
        assertEquals("db:tool-result-1", metadata.get("fullOutputRef"));
        assertEquals("output-hash", metadata.get("outputHash"));
    }

    @Test
    void optionalAdaptersReportUnavailableWithoutCollaborator() {
        assertFalse(new OpsControlledCodeToolResultAdapter(provider(null)).available());
        assertFalse(new OpsControlledCodeProofAdapter(provider(null)).available());
        new OpsControlledCodeAuditAdapter(provider(null)).record(null);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
