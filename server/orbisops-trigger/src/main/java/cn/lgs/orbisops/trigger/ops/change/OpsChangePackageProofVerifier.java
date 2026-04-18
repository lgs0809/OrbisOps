package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageApprovalOperationPolicy;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;

import java.util.List;

/** Typed approval proof orchestrator; external facts are delegated to bounded adapters. */
public final class OpsChangePackageProofVerifier {

    private static final OpsChangePackageApprovalSnapshotReader SNAPSHOT_READER =
            new OpsChangePackageApprovalSnapshotReader();
    private static final OpsChangePackageApprovalOperationMapper OPERATION_MAPPER =
            new OpsChangePackageApprovalOperationMapper();
    private static final ChangePackageApprovalOperationPolicy OPERATION_POLICY =
            new ChangePackageApprovalOperationPolicy();

    private final OpsChangePackageRepairApprovalVerifier repairApprovalVerifier;

    public OpsChangePackageProofVerifier(OpsRepairWorkspaceService repairWorkspaceService,
                                         OpsTrustedProofService trustedProofService) {
        OpsChangePackageApprovalProofService repairProofService =
                new OpsChangePackageApprovalProofService(trustedProofService);
        this.repairApprovalVerifier = new OpsChangePackageRepairApprovalVerifier(
                repairWorkspaceService,
                repairProofService,
                SNAPSHOT_READER);
    }

    public void verifyPackageBeforeApprove(ChangePackageCurrent current,
                                           ChangePackageVersion targetVersion) {
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        if (targetVersion == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_REQUIRED");
        OpsChangePackageApprovalSnapshotReader.ApprovalSnapshot approvalSnapshot =
                SNAPSHOT_READER.read(targetVersion);
        ChangePackageType type = current.packageType();
        if (type.humanOnly()) return;
        if (type == ChangePackageType.GIT_BRANCH_REPAIR) {
            repairApprovalVerifier.verify(current, targetVersion, approvalSnapshot.values());
            return;
        }
        if (!type.executable()) return;
        if (approvalSnapshot.operations().isEmpty()) {
            throw new IllegalStateException("变更包缺少 mcpSteps/landingPlan operations");
        }
        List<ChangePackageApprovalOperation> operations =
                OPERATION_MAPPER.map(approvalSnapshot.operations());
        OPERATION_POLICY.verify(
                current.state().value(ChangePackageCurrentField.RISK_LEVEL),
                operations);
    }
}
