package cn.lgs.orbisops.domain.changepackage.model;

import java.util.List;

public record ChangePackagePreparationContext(ChangePackagePreparationProof preflight,
                                              ChangePackagePreparationProof dryRun,
                                              List<ChangePackagePreparationOperation> operations,
                                              boolean toolBindingsComplete,
                                              boolean trustedEvidencePresent,
                                              boolean repairIntent,
                                              String requestedPackageType,
                                              String requestedRiskLevel,
                                              List<String> changedFiles) {

    public ChangePackagePreparationContext {
        preflight = preflight == null ? new ChangePackagePreparationProof("", "", true) : preflight;
        dryRun = dryRun == null ? new ChangePackagePreparationProof("", "", true) : dryRun;
        operations = operations == null ? List.of() : List.copyOf(operations);
        requestedPackageType = value(requestedPackageType);
        requestedRiskLevel = value(requestedRiskLevel);
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }

    public boolean requiresTrustedValidationProof() {
        return operations.stream().anyMatch(ChangePackagePreparationOperation::requiresTrustedValidationProof);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
