package cn.lgs.orbisops.application.changepackage;

/** Typed boundary for one pre-approval validation orchestration. */
public interface ChangePackageValidationPort {

    ChangePackagePreApprovalValidationOutcome validate(
            ChangePackageCommands.Validate command);
}
