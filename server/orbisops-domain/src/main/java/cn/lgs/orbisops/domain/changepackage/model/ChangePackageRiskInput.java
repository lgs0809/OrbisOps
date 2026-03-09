package cn.lgs.orbisops.domain.changepackage.model;

import java.util.List;

public record ChangePackageRiskInput(String requestedRiskLevel,
                                     List<ChangePackageOperationRisk> operations,
                                     List<String> changedFiles) {

    public ChangePackageRiskInput {
        requestedRiskLevel = requestedRiskLevel == null ? "" : requestedRiskLevel.trim();
        operations = operations == null ? List.of() : List.copyOf(operations);
        changedFiles = changedFiles == null ? List.of() : changedFiles.stream()
                .map(value -> value == null ? "" : value.trim()).filter(value -> !value.isBlank()).toList();
    }
}
