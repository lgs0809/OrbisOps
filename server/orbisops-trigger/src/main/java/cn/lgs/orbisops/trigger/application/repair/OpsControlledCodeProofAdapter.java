package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeProofPort;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsControlledCodeProofAdapter implements ControlledCodeProofPort {

    private final OpsTrustedProofService proofs;

    public OpsControlledCodeProofAdapter(ObjectProvider<OpsTrustedProofService> provider) {
        this.proofs = provider.getIfAvailable();
    }

    @Override
    public boolean available() {
        return proofs != null;
    }

    @Override
    public void record(ControlledCodeProof proof, String actor) {
        if (proofs == null) throw new IllegalStateException("TRUSTED_PROOF_STORE_UNAVAILABLE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", proof.projectId());
        payload.put("packageId", proof.packageId());
        payload.put("packageVersion", proof.packageVersion());
        payload.put("packageHash", proof.packageHash());
        payload.put("riskLevel", proof.riskLevel());
        payload.put("proofType", "CONTROLLED_BASH_TEST");
        payload.put("source", "CONTROLLED_BASH_EXECUTED");
        payload.put("externalRunId", proof.externalRunId());
        payload.put("commandHash", proof.commandHash());
        payload.put("resultStatus", "PASSED");
        payload.put("metadata", Map.of(
                "toolName", "code_bash",
                "expectedEffect", proof.expectedEffect().name(),
                "resultId", proof.resultId(),
                "fullOutputRef", proof.fullOutputRef(),
                "outputHash", proof.outputHash(),
                "exitCode", proof.exitCode(),
                "workspaceId", proof.workspaceId()));
        proofs.recordTrustedProof(payload, actor);
    }
}
