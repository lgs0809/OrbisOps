package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationAssessment;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationContext;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationDecision;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageRiskInput;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure preparation decision policy. External evidence acquisition belongs to an adapter;
 * this policy owns the assessment, package classification and fail-closed limitations.
 */
public final class ChangePackagePreparationPolicy {

    private static final List<String> RISK_ORDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final ChangePackageRiskPolicy riskPolicy;
    private final List<PreparationSpecification> specifications;

    public ChangePackagePreparationPolicy() {
        this(new ChangePackageRiskPolicy(), List.of(
                new OperationPresenceSpecification(),
                new ToolBindingSpecification(),
                new PreflightSpecification(),
                new ValidationProofSpecification(),
                new OperationShapeSpecification(),
                new TrustedEvidenceSpecification(),
                new RequestedTypeSpecification()));
    }

    ChangePackagePreparationPolicy(ChangePackageRiskPolicy riskPolicy,
                                   List<PreparationSpecification> specifications) {
        this.riskPolicy = riskPolicy;
        this.specifications = List.copyOf(specifications);
    }

    public ChangePackagePreparationDecision decide(ChangePackagePreparationContext context) {
        if (context == null) throw new IllegalArgumentException("CHANGE_PACKAGE_PREPARATION_CONTEXT_REQUIRED");

        List<String> limitations = specifications.stream()
                .flatMap(specification -> specification.violations(context).stream())
                .distinct()
                .toList();
        ChangePackagePreparationAssessment assessment = assessment(context, limitations);
        ChangePackageType packageType = packageType(context, assessment);
        ChangePackageStatus status = assessment == ChangePackagePreparationAssessment.ACCEPTABLE
                ? ChangePackageStatus.READY_FOR_REVIEW
                : ChangePackageStatus.VALIDATION_FAILED;
        String defaultRisk = assessment == ChangePackagePreparationAssessment.ACCEPTABLE ? "MEDIUM" : "HIGH";
        String requestedRisk = normalizeRisk(context.requestedRiskLevel());
        String riskSeed = maxRisk(defaultRisk, requestedRisk);
        String effectiveRisk = riskPolicy.assess(new ChangePackageRiskInput(
                riskSeed,
                context.operations().stream().map(ChangePackagePreparationOperation::toRisk).toList(),
                context.changedFiles())).effectiveRiskLevel();
        boolean escalated = !requestedRisk.isBlank() && rank(requestedRisk) < rank(effectiveRisk);

        return new ChangePackagePreparationDecision(
                assessment,
                packageType,
                status,
                effectiveRisk,
                limitations,
                reasonCode(assessment, limitations),
                escalated);
    }

    private ChangePackagePreparationAssessment assessment(ChangePackagePreparationContext context,
                                                           List<String> limitations) {
        boolean proofSatisfied = !context.requiresTrustedValidationProof()
                || context.dryRun().passed();
        if (context.preflight().passed()
                && !context.operations().isEmpty()
                && proofSatisfied
                && limitations.isEmpty()) {
            return ChangePackagePreparationAssessment.ACCEPTABLE;
        }
        if (failed(context.preflight().status())
                || (context.requiresTrustedValidationProof() && failed(context.dryRun().status()))) {
            return ChangePackagePreparationAssessment.NEEDS_REFINEMENT;
        }
        if (validationRequired(context, limitations)) {
            return ChangePackagePreparationAssessment.VALIDATION_REQUIRED;
        }
        return ChangePackagePreparationAssessment.MANUAL_REQUIRED;
    }

    private boolean validationRequired(ChangePackagePreparationContext context,
                                       List<String> limitations) {
        if (!context.requiresTrustedValidationProof()
                || context.dryRun().passed()
                || failed(context.dryRun().status())
                || limitations.isEmpty()) {
            return false;
        }
        return limitations.stream().allMatch(item -> {
            String value = item == null ? "" : item.toLowerCase(Locale.ROOT);
            return value.contains("validation") || value.contains("dry-run");
        });
    }

    private ChangePackageType packageType(ChangePackagePreparationContext context,
                                          ChangePackagePreparationAssessment assessment) {
        ChangePackageType requested = parseType(context.requestedPackageType());
        if (requested != null && safeRequestedType(requested, assessment, !context.operations().isEmpty())) {
            return requested;
        }
        if ((assessment == ChangePackagePreparationAssessment.ACCEPTABLE
                || assessment == ChangePackagePreparationAssessment.VALIDATION_REQUIRED)
                && !context.operations().isEmpty()) {
            return ChangePackageType.MCP_OPERATION_PACKAGE;
        }
        if (assessment == ChangePackagePreparationAssessment.MANUAL_REQUIRED) {
            return ChangePackageType.MANUAL_REQUIRED;
        }
        return ChangePackageType.NEEDS_HUMAN_DESIGN;
    }

    private boolean safeRequestedType(ChangePackageType requested,
                                      ChangePackagePreparationAssessment assessment,
                                      boolean hasExecutableSteps) {
        if (requested == ChangePackageType.NO_ACTION_REQUIRED
                || requested == ChangePackageType.MANUAL_REQUIRED
                || requested == ChangePackageType.NEEDS_HUMAN_DESIGN) {
            return true;
        }
        return (assessment == ChangePackagePreparationAssessment.ACCEPTABLE
                || assessment == ChangePackagePreparationAssessment.VALIDATION_REQUIRED)
                && hasExecutableSteps;
    }

    private String reasonCode(ChangePackagePreparationAssessment assessment, List<String> limitations) {
        if (assessment == ChangePackagePreparationAssessment.ACCEPTABLE) return "READY_FOR_REVIEW";
        if (limitations.stream().anyMatch(item -> item.contains("mcpSteps"))) return "MISSING_EXECUTION_STEPS";
        if (limitations.stream().anyMatch(item -> item.contains("tool binding") || item.contains("schema"))) {
            return "MISSING_TOOL_BINDING_OR_SCHEMA";
        }
        if (limitations.stream().anyMatch(item -> item.contains("validation") || item.contains("dry-run"))) {
            return "MISSING_VALIDATION_PROOF";
        }
        if (assessment == ChangePackagePreparationAssessment.NEEDS_REFINEMENT) return "VALIDATION_FAILED";
        return "MANUAL_CONFIRMATION_REQUIRED";
    }

    private ChangePackageType parseType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ChangePackageType.require(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean failed(String value) {
        return "FAILED".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private String normalizeRisk(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return RISK_ORDER.contains(normalized) ? normalized : "";
    }

    private String maxRisk(String left, String right) {
        return rank(right) > rank(left) ? right : left;
    }

    private int rank(String value) {
        return RISK_ORDER.indexOf(normalizeRisk(value));
    }

    interface PreparationSpecification {
        List<String> violations(ChangePackagePreparationContext context);
    }

    private static final class RequestedTypeSpecification implements PreparationSpecification {
        @Override
        public List<String> violations(ChangePackagePreparationContext context) {
            if (context.operations().isEmpty() || context.requestedPackageType().isBlank()) return List.of();
            try {
                return ChangePackageType.require(context.requestedPackageType()).humanOnly()
                        ? List.of("HUMAN_ONLY_PACKAGE_HAS_EXECUTABLE_OPERATIONS：人工处理类型不能包含可执行操作。")
                        : List.of();
            } catch (IllegalArgumentException invalidType) {
                return List.of(); // Existing classification fallback handles unknown type hints.
            }
        }
    }

    private static final class OperationPresenceSpecification implements PreparationSpecification {
        @Override
        public List<String> violations(ChangePackagePreparationContext context) {
            return context.operations().isEmpty()
                    ? List.of("缺少结构化 mcpSteps，不能自动落地执行。")
                    : List.of();
        }
    }

    private static final class ToolBindingSpecification implements PreparationSpecification {
        @Override
        public List<String> violations(ChangePackagePreparationContext context) {
            return context.toolBindingsComplete()
                    ? List.of()
                    : List.of("缺少完整 MCP tool binding/schema policy，不能证明执行参数可用。");
        }
    }

    private static final class PreflightSpecification implements PreparationSpecification {
        @Override
        public List<String> violations(ChangePackagePreparationContext context) {
            return context.preflight().passed() ? List.of() : List.of("preflight 未通过。");
        }
    }

    private static final class ValidationProofSpecification implements PreparationSpecification {
        @Override
        public List<String> violations(ChangePackagePreparationContext context) {
            if (!context.requiresTrustedValidationProof()
                    || context.dryRun().passed()) {
                return List.of();
            }
            return List.of("包中显式 validation operation 缺少可信 validation proof。");
        }
    }

    private static final class OperationShapeSpecification implements PreparationSpecification {
        @Override
        public List<String> violations(ChangePackagePreparationContext context) {
            List<String> violations = new ArrayList<>();
            for (ChangePackagePreparationOperation operation : context.operations()) {
                if (!operation.completeShape()) {
                    violations.add("MCP 步骤 " + operation.operationId()
                            + " 缺少 toolName/mcpId/effect/resourceScope 等关键字段。");
                }
            }
            return violations;
        }
    }

    private static final class TrustedEvidenceSpecification implements PreparationSpecification {
        @Override
        public List<String> violations(ChangePackagePreparationContext context) {
            boolean evidenceRequired = !context.operations().isEmpty() || context.repairIntent();
            return evidenceRequired && !context.trustedEvidencePresent()
                    ? List.of("当前 Run 没有由 ToolExecution/ToolResultStore 生成的可信 Evidence，不能生成可自动落地的 ChangePackage")
                    : List.of();
        }
    }
}
