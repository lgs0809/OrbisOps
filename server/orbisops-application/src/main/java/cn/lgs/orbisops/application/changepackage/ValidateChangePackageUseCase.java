package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

/** Application facade over typed pre-approval validation orchestration. */
public final class ValidateChangePackageUseCase {

    private final ChangePackageValidationPort port;

    public ValidateChangePackageUseCase(ChangePackageValidationPort port) {
        if (port == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATION_PORT_REQUIRED");
        this.port = port;
    }

    public Map<String, Object> validate(ChangePackageCommands.Validate command) {
        return validateOutcome(command).packageView();
    }

    public ChangePackagePreApprovalValidationOutcome validateOutcome(
            ChangePackageCommands.Validate command) {
        if (command == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATE_COMMAND_REQUIRED");
        return port.validate(command);
    }
}
