package cn.lgs.orbisops.trigger.ops.change;

import java.util.List;
import java.util.Map;

/** Builds stable validation reports and revision guidance from validation facts. */
final class OpsPreApprovalValidationReportFactory {

    ValidationResult pass(String reasonCode,
                          List<Map<String, Object>> proofs,
                          List<String> warnings) {
        return new ValidationResult(true, Map.of(
                "status", "PASSED",
                "reasonCode", reasonCode,
                "trustedProofRefs", List.copyOf(proofs),
                "warnings", List.copyOf(warnings)));
    }

    ValidationResult fail(List<String> errors,
                          List<Map<String, Object>> proofs) {
        List<String> frozenErrors = List.copyOf(errors);
        return new ValidationResult(false, Map.of(
                "status", "FAILED",
                "reasonCode", "VALIDATION_FAILED",
                "errors", frozenErrors,
                "trustedProofRefs", List.copyOf(proofs),
                "reviseHints", reviseHints(frozenErrors)));
    }

    private List<String> reviseHints(List<String> errors) {
        return errors.stream().map(error -> {
            if (error.startsWith("MISSING_TRUSTED")) {
                return "代码修复请补受控 Bash / CI test proof；显式 validation operation 请补真实 dry-run / validation proof 后重新验证。";
            }
            if (error.startsWith("MISSING_OPERATION_FIELD")) {
                return "请补齐 operation snapshot 的 tool、effect、risk、resourceScope、targetEnvironment 和 arguments。";
            }
            return "请回到审核前 workflow 修订 ChangePackage：" + error;
        }).distinct().toList();
    }

    record ValidationResult(boolean passed, Map<String, Object> report) {
        ValidationResult {
            if (report == null) throw new IllegalArgumentException("VALIDATION_REPORT_REQUIRED");
            report = Map.copyOf(report);
        }
    }
}
