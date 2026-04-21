package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;

public interface ControlledCodeProofPort {

    boolean available();

    void record(ControlledCodeProof proof, String actor);

    record ControlledCodeProof(
            String projectId,
            String workspaceId,
            String packageId,
            int packageVersion,
            String packageHash,
            String riskLevel,
            String externalRunId,
            String commandHash,
            ControlledCodeEffect expectedEffect,
            String resultId,
            String fullOutputRef,
            String outputHash,
            int exitCode) {
    }
}
