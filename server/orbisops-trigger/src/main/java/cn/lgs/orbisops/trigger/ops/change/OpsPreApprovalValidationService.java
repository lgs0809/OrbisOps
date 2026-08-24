package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ChangePackagePreApprovalValidationOutcome;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationOutcome;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationReport;
import cn.lgs.orbisops.application.changepackage.ChangePackageValidationWritebackUseCase;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationOperationAssessment;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackagePreApprovalValidationPolicy;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpsPreApprovalValidationService implements ChangePackageValidationPort {

    private static final OpsPreApprovalStructuredValueReader STRUCTURED_VALUE_READER =
            new OpsPreApprovalStructuredValueReader();
    private static final OpsPreApprovalValidationReportFactory REPORT_FACTORY =
            new OpsPreApprovalValidationReportFactory();
    private static final OpsPreApprovalOperationMapper OPERATION_MAPPER =
            new OpsPreApprovalOperationMapper();
    private static final ChangePackagePreApprovalValidationPolicy VALIDATION_POLICY =
            new ChangePackagePreApprovalValidationPolicy();

    private final OpsPreApprovalSnapshotReader snapshotReader;
    private final ChangePackageValidationWritebackUseCase validationWritebackUseCase;
    private final OpsPreApprovalRepairValidationExecutor repairValidationExecutor;
    private final OpsPreApprovalMcpValidationExecutor mcpValidationExecutor;

    @Autowired
    public OpsPreApprovalValidationService(ChangePackageQueryService queryService,
                                           ChangePackageValidationWritebackUseCase validationWritebackUseCase,
                                           ObjectProvider<OpsRepairWorkspaceService> repairWorkspaceServiceProvider,
                                           ObjectProvider<OpsTrustedProofService> trustedProofServiceProvider,
                                           ObjectProvider<OpsToolExecutionService> toolExecutionServiceProvider,
                                           ObjectProvider<OpsProjectMcpRuntimeConfigService> projectMcpRuntimeConfigServiceProvider) {
        this.snapshotReader = new OpsPreApprovalSnapshotReader(queryService);
        this.validationWritebackUseCase = validationWritebackUseCase;
        OpsPreApprovalProofService proofService =
                new OpsPreApprovalProofService(trustedProofServiceProvider.getIfAvailable());
        OpsToolExecutionService toolExecutionService = toolExecutionServiceProvider.getIfAvailable();
        this.repairValidationExecutor = new OpsPreApprovalRepairValidationExecutor(
                repairWorkspaceServiceProvider.getIfAvailable(),
                toolExecutionService,
                proofService);
        this.mcpValidationExecutor = new OpsPreApprovalMcpValidationExecutor(
                toolExecutionService,
                projectMcpRuntimeConfigServiceProvider.getIfAvailable(),
                proofService);
    }

    public OpsPreApprovalValidationService(ChangePackageQueryService queryService,
                                           ChangePackageValidationWritebackUseCase validationWritebackUseCase,
                                           ObjectProvider<OpsRepairWorkspaceService> repairWorkspaceServiceProvider,
                                           ObjectProvider<OpsTrustedProofService> trustedProofServiceProvider) {
        this.snapshotReader = new OpsPreApprovalSnapshotReader(queryService);
        this.validationWritebackUseCase = validationWritebackUseCase;
        OpsPreApprovalProofService proofService =
                new OpsPreApprovalProofService(trustedProofServiceProvider.getIfAvailable());
        this.repairValidationExecutor = new OpsPreApprovalRepairValidationExecutor(
                repairWorkspaceServiceProvider.getIfAvailable(),
                null,
                proofService);
        this.mcpValidationExecutor = new OpsPreApprovalMcpValidationExecutor(
                null,
                null,
                proofService);
    }

    @Override
    public ChangePackagePreApprovalValidationOutcome validate(
            ChangePackageCommands.Validate command) {
        if (command == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATE_COMMAND_REQUIRED");
        return validateOutcome(command.packageId(), command.version(), command.actor());
    }

    public Map<String, Object> validate(String packageId, int version, String actor) {
        return validateOutcome(packageId, version, actor).packageView();
    }

    public ChangePackagePreApprovalValidationOutcome validateOutcome(
            String packageId,
            int version,
            String actor) {
        OpsPreApprovalSnapshotReader.ValidationSnapshot validationSnapshot =
                snapshotReader.load(packageId, version);
        OpsPreApprovalValidationReportFactory.ValidationResult result = validateSnapshot(
                validationSnapshot.current(),
                validationSnapshot.snapshot(),
                actor);
        Map<String, Object> signedReport = new LinkedHashMap<>(result.report());
        signedReport.put("_validationExecutionToken", validationWritebackUseCase.executionToken(
                packageId,
                validationSnapshot.version(),
                validationSnapshot.packageHash()));
        ChangePackageValidationReport report = ChangePackageValidationReport.from(signedReport);
        ChangePackageValidationOutcome writebackOutcome = validationWritebackUseCase.writeBack(
                new ChangePackageCommands.ValidationWriteback(
                        packageId,
                        result.passed(),
                        signedReport,
                        actor));
        return new ChangePackagePreApprovalValidationOutcome(
                writebackOutcome,
                report,
                snapshotReader.detail(packageId));
    }

    private OpsPreApprovalValidationReportFactory.ValidationResult validateSnapshot(
            Map<String, Object> current,
            Map<String, Object> snapshot,
            String actor) {
        String packageType = text(firstNonNull(
                snapshot.get("packageType"),
                current.get("package_type"),
                current.get("packageType")), "");
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> proofs = new ArrayList<>();
        ChangePackageType type;
        try {
            type = ChangePackageType.require(packageType);
        } catch (IllegalArgumentException ignored) {
            errors.add("UNKNOWN_PACKAGE_TYPE：" + packageType);
            return REPORT_FACTORY.fail(errors, proofs);
        }
        if (type.humanOnly()) {
            // Failed executable proposals retain their steps for revision; their downgraded type
            // must never turn a missing validation proof into a no-op validation success.
            if (hasRetainedOperations(snapshot)) {
                return REPORT_FACTORY.fail(List.of("HUMAN_ONLY_PACKAGE_HAS_EXECUTABLE_OPERATIONS"), proofs);
            }
            return REPORT_FACTORY.pass("NO_EXECUTABLE_OPERATION", List.of(), List.of());
        }
        if (type == ChangePackageType.GIT_BRANCH_REPAIR) {
            merge(repairValidationExecutor.validate(snapshot, actor), errors, proofs);
        } else if (type.executable()) {
            validateMcpPackage(snapshot, errors, proofs, actor);
        } else {
            errors.add("UNKNOWN_PACKAGE_TYPE：" + packageType);
        }
        return errors.isEmpty()
                ? REPORT_FACTORY.pass("READY_FOR_REVIEW", proofs, List.of())
                : REPORT_FACTORY.fail(errors, proofs);
    }

    private boolean hasRetainedOperations(Map<String, Object> snapshot) {
        for (String key : List.of("mcpSteps", "mcpStepsJson", "operations")) {
            if (!STRUCTURED_VALUE_READER.operations(snapshot.get(key)).isEmpty()) return true;
        }
        for (String key : List.of("landingPlan", "landingPlanJson")) {
            Map<String, Object> plan = STRUCTURED_VALUE_READER.object(snapshot.get(key));
            for (String steps : List.of("steps", "operations", "mcpSteps")) {
                if (!STRUCTURED_VALUE_READER.operations(plan.get(steps)).isEmpty()) return true;
            }
        }
        return false;
    }

    private void validateMcpPackage(Map<String, Object> snapshot,
                                    List<String> errors,
                                    List<Map<String, Object>> proofs,
                                    String actor) {
        List<Map<String, Object>> operations = STRUCTURED_VALUE_READER.operations(firstNonNull(
                snapshot.get("mcpSteps"),
                snapshot.get("mcpStepsJson"),
                STRUCTURED_VALUE_READER.object(snapshot.get("landingPlan")).get("mcpSteps")));
        if (operations.isEmpty()) {
            errors.add("MISSING_MCP_OPERATIONS");
            return;
        }
        List<OpsPreApprovalOperationMapper.MappedOperation> mappedOperations =
                OPERATION_MAPPER.map(operations);
        ChangePackageValidationOperationAssessment assessment = VALIDATION_POLICY.assess(
                text(snapshot.get("riskLevel"), "MEDIUM"),
                mappedOperations.stream()
                        .map(OpsPreApprovalOperationMapper.MappedOperation::facts)
                        .toList());
        errors.addAll(assessment.errors());
        if (!assessment.valid()) return;
        boolean explicitValidationStep = mappedOperations.stream()
                .anyMatch(operation -> VALIDATION_POLICY.validationExecutable(operation.facts()));
        if (!explicitValidationStep) return;
        merge(mcpValidationExecutor.obtainProof(snapshot, mappedOperations, actor), errors, proofs);
    }

    private void merge(OpsPreApprovalValidationExecutionResult result,
                       List<String> errors,
                       List<Map<String, Object>> proofs) {
        if (result == null) return;
        errors.addAll(result.errors());
        proofs.addAll(result.proofs());
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }
}
