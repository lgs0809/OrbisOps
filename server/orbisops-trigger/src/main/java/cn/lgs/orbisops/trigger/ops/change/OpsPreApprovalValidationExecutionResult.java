package cn.lgs.orbisops.trigger.ops.change;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable result from a bounded pre-approval validation executor. */
record OpsPreApprovalValidationExecutionResult(boolean proofObtained,
                                               List<String> errors,
                                               List<Map<String, Object>> proofs) {

    OpsPreApprovalValidationExecutionResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        proofs = proofs == null
                ? List.of()
                : proofs.stream()
                .map(item -> item == null ? Map.<String, Object>of() : Map.copyOf(new LinkedHashMap<>(item)))
                .toList();
    }

    static OpsPreApprovalValidationExecutionResult failed(String error) {
        return new OpsPreApprovalValidationExecutionResult(false, List.of(error), List.of());
    }

    static OpsPreApprovalValidationExecutionResult completed(boolean proofObtained,
                                                              List<String> errors,
                                                              List<Map<String, Object>> proofs) {
        return new OpsPreApprovalValidationExecutionResult(proofObtained, errors, proofs);
    }
}
