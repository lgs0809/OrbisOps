package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

/** Public application API for review actions. */
public final class ReviewChangePackageUseCase {

    private final ChangePackageApprovalUseCase approvalUseCase;
    private final ChangePackageQueryPort queryPort;

    public ReviewChangePackageUseCase(ChangePackageApprovalUseCase approvalUseCase,
                                      ChangePackageQueryPort queryPort) {
        if (approvalUseCase == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVAL_USE_CASE_REQUIRED");
        }
        if (queryPort == null) throw new IllegalArgumentException("CHANGE_PACKAGE_QUERY_PORT_REQUIRED");
        this.approvalUseCase = approvalUseCase;
        this.queryPort = queryPort;
    }

    public Map<String, Object> submitReview(ChangePackageCommands.SubmitReview command) {
        approvalUseCase.submitReview(command);
        return queryPort.detail(command.packageId());
    }

    public Map<String, Object> approve(ChangePackageCommands.Approve command) {
        approvalUseCase.approve(command);
        return queryPort.detail(command.packageId());
    }

    public Map<String, Object> reject(ChangePackageCommands.Reject command) {
        approvalUseCase.reject(command);
        return queryPort.detail(command.packageId());
    }
}
