package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.capability.CapabilityDependencyReadiness;
import cn.lgs.orbisops.application.capability.CapabilityReadinessSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Combines project-local capability facts with platform runtime readiness. The
 * projection is informational only and never changes Runtime Authority.
 */
public final class ProjectProductReadinessApplicationService {

    private final ProjectEmergencyStopAcceptancePort emergencyStopAcceptancePort;
    private final ProjectModelReadinessPort modelReadinessPort;

    /**
     * Keeps the application service usable in isolated unit tests and in
     * deployments that have not wired the optional audit fact projection.
     * Those deployments remain fail-closed for environment validation.
     */
    public ProjectProductReadinessApplicationService() {
        this(
                projectId -> ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact.notValidated(),
                () -> false);
    }

    public ProjectProductReadinessApplicationService(ProjectEmergencyStopAcceptancePort emergencyStopAcceptancePort) {
        this(emergencyStopAcceptancePort, () -> false);
    }

    public ProjectProductReadinessApplicationService(
            ProjectEmergencyStopAcceptancePort emergencyStopAcceptancePort,
            ProjectModelReadinessPort modelReadinessPort) {
        this.emergencyStopAcceptancePort = emergencyStopAcceptancePort == null
                ? projectId -> ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact.notValidated()
                : emergencyStopAcceptancePort;
        this.modelReadinessPort = modelReadinessPort == null ? () -> false : modelReadinessPort;
    }

    public ProjectProductReadinessProjection project(
            ProjectWorkspaceProjection workspace,
            CapabilityReadinessSnapshot platform) {
        if (workspace == null) throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECTION_REQUIRED");
        if (platform == null) throw new IllegalArgumentException("CAPABILITY_READINESS_SNAPSHOT_REQUIRED");

        boolean liveEvidenceConfigured = workspace.dataResourceCount() > 0;
        boolean realQueryVerified = workspace.onboarding().stream()
                .anyMatch(step -> "query-proof".equals(step.key()) && step.completed());
        boolean modelConfigured = modelAvailable();
        boolean runtimeEvidence = platform.analysisReady().ready();

        List<ProjectProductReadinessProjection.Check> diagnosisChecks = List.of(
                check("DEFAULT_AGENT", "默认智能助手", workspace.defaultAgentPublished(),
                        workspace.defaultAgentPublished()
                                ? ProjectProductReadinessProjection.ReadinessLevel.CONFIGURED
                                : ProjectProductReadinessProjection.ReadinessLevel.NOT_CONFIGURED,
                        workspace.defaultAgentPublished() ? "项目默认 Agent 已发布" : "先发布项目默认 Agent"),
                check("MODEL", "Model", modelConfigured && realQueryVerified,
                        modelConfigured && realQueryVerified
                                ? ProjectProductReadinessProjection.ReadinessLevel.QUERY_VERIFIED
                                : modelConfigured
                                ? ProjectProductReadinessProjection.ReadinessLevel.CONFIGURED
                                : ProjectProductReadinessProjection.ReadinessLevel.NOT_CONFIGURED,
                        modelConfigured && realQueryVerified
                                ? "已通过成功的 ReAct 真实证据诊断运行证明模型执行链路可用"
                                : modelConfigured
                                ? "已配置可用 Chat Model 与 Provider，但尚未形成成功的真实证据诊断运行证明"
                                : "尚未配置可用 Chat Model 与 Provider"),
                check("REAL_EVIDENCE", "实时 Evidence Source", liveEvidenceConfigured && realQueryVerified,
                        realQueryVerified
                                ? ProjectProductReadinessProjection.ReadinessLevel.QUERY_VERIFIED
                                : liveEvidenceConfigured
                                ? ProjectProductReadinessProjection.ReadinessLevel.CONFIGURED
                                : ProjectProductReadinessProjection.ReadinessLevel.NOT_CONFIGURED,
                        realQueryVerified
                                ? "已完成一次 resultId/outputHash 绑定的真实数据查询与结构化 Evidence 诊断"
                                : liveEvidenceConfigured
                                ? "已配置实时日志/指标/数据库数据源，但尚未完成一次真实查询验证"
                                : "至少接入一个真实日志、指标或数据库数据源；知识库/代码仓库不能单独证明当前生产状态"),
                check("RUNTIME_TOOL_BINDING", "Runtime / Tool binding", runtimeEvidence,
                        runtimeEvidence
                                ? ProjectProductReadinessProjection.ReadinessLevel.CONNECTIVITY_VERIFIED
                                : ProjectProductReadinessProjection.ReadinessLevel.NOT_CONFIGURED,
                        runtimeEvidence ? "ToolResult / Audit 等诊断运行依赖已通过平台探针" : reasons(platform.analysisReady().reasonCodes())));
        ProjectProductReadinessProjection.Readiness diagnosis = readiness(
                diagnosisChecks,
                firstMissingAction(diagnosisChecks, "开始第一次真实诊断"));

        CapabilityDependencyReadiness toolRuntime = dependency(platform, "toolRuntimeProfile");
        CapabilityDependencyReadiness auditStore = dependency(platform, "auditStore");
        CapabilityDependencyReadiness resultStore = dependency(platform, "toolResultStore");
        boolean productionBinding = workspace.executionResourceCount() > 0 && toolRuntime.up();
        boolean packageReady = platform.changePackageReady().ready();
        boolean landingReady = platform.approvedLandingReady().ready();
        boolean ledgerReady = resultStore.up()
                && !contains(platform.approvedLandingReady().reasonCodes(), "LANDING_OPERATION_JOURNAL_UNAVAILABLE");
        boolean reconciliationReady = toolRuntime.up()
                && !contains(platform.approvedLandingReady().reasonCodes(), "LANDING_OPERATION_RECOVERY_UNAVAILABLE");
        boolean auditReady = auditStore.up();
        boolean emergencyStopGovernanceConfigured = toolRuntime.up();
        ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact emergencyStopAcceptance =
                acceptanceFact(workspace.project().projectId());
        boolean emergencyStopEnvironmentValidated = emergencyStopGovernanceConfigured
                && emergencyStopAcceptance.accepted();

        List<ProjectProductReadinessProjection.Check> remediationChecks = List.of(
                check("DIAGNOSIS", "诊断能力", diagnosis.ready(),
                        diagnosis.ready() ? "诊断前置能力已就绪" : "先完成诊断能力 Checklist"),
                check("CHANGE_PACKAGE_APPROVAL", "ChangePackage / Approval", packageReady,
                        packageReady ? "变更任务书、可信证明与审批前置存储可用" : reasons(platform.changePackageReady().reasonCodes())),
                check("PROD_TOOL_MCP", "PROD Tool / MCP 与权限", productionBinding,
                        productionBinding
                                ? "项目存在生产执行资源，平台生产 Tool profile 已通过 fail-closed readiness"
                                : workspace.executionResourceCount() <= 0
                                ? "项目尚未配置生产执行资源"
                                : reason(toolRuntime)),
                check("LANDING_RUNTIME", "Landing runtime", landingReady,
                        landingReady ? "platform-landing-react 所需 Landing 基础能力已就绪" : reasons(platform.approvedLandingReady().reasonCodes())),
                check("TOOL_EXECUTION_LEDGER", "ToolExecution ledger", ledgerReady,
                        ledgerReady ? "Tool result store 与 Landing operation journal readiness 已通过" : "ToolExecution 结果或 Landing journal 尚未就绪"),
                check("VERIFICATION_RECONCILIATION", "Verification / reconciliation", reconciliationReady,
                        reconciliationReady
                                ? "生产 Tool governance 与 Landing recovery/reconciliation 基础能力可用"
                                : "恢复验证或 UNKNOWN reconciliation 基础能力尚未就绪"),
                check("EMERGENCY_STOP", "Emergency Stop 治理", emergencyStopEnvironmentValidated,
                        emergencyStopEnvironmentValidated
                                ? ProjectProductReadinessProjection.ReadinessLevel.ENVIRONMENT_VALIDATED
                                : emergencyStopGovernanceConfigured
                                ? ProjectProductReadinessProjection.ReadinessLevel.CONFIGURED
                                : ProjectProductReadinessProjection.ReadinessLevel.NOT_CONFIGURED,
                        emergencyStopEnvironmentValidated
                                ? emergencyStopAcceptance.detail()
                                : emergencyStopGovernanceConfigured
                                ? emergencyStopAcceptance.detail()
                                : "生产 Tool profile 未就绪，不能证明急停治理边界可用"),
                check("AUDIT", "Audit", auditReady,
                        auditReady ? "审计存储可用" : reason(auditStore)));
        ProjectProductReadinessProjection.Readiness remediation = readiness(
                remediationChecks,
                firstMissingAction(remediationChecks, "生产处置能力已就绪"));
        return new ProjectProductReadinessProjection(diagnosis, remediation);
    }

    private ProjectProductReadinessProjection.Readiness readiness(
            List<ProjectProductReadinessProjection.Check> checks,
            String nextAction) {
        List<String> missing = checks.stream()
                .filter(check -> !check.ready())
                .map(ProjectProductReadinessProjection.Check::label)
                .toList();
        return new ProjectProductReadinessProjection.Readiness(
                missing.isEmpty(), checks, missing, nextAction);
    }

    private String firstMissingAction(
            List<ProjectProductReadinessProjection.Check> checks,
            String readyAction) {
        return checks.stream()
                .filter(check -> !check.ready())
                .findFirst()
                .map(check -> switch (check.key()) {
                    case "DEFAULT_AGENT" -> "发布项目默认智能助手";
                    case "MODEL" -> check.level() == ProjectProductReadinessProjection.ReadinessLevel.NOT_CONFIGURED
                            ? "配置并验证模型 Provider"
                            : "运行一次真实诊断验证模型执行链路";
                    case "REAL_EVIDENCE" -> check.level() == ProjectProductReadinessProjection.ReadinessLevel.NOT_CONFIGURED
                            ? "接入并测试一个真实数据源"
                            : "运行一次真实诊断验证数据源";
                    case "RUNTIME_TOOL_BINDING" -> "修复诊断 Runtime / Tool 依赖";
                    case "DIAGNOSIS" -> "先完成诊断能力";
                    case "PROD_TOOL_MCP" -> "配置生产处置能力";
                    default -> "完善生产处置安全链路";
                })
                .orElse(readyAction);
    }

    private ProjectProductReadinessProjection.Check check(
            String key,
            String label,
            boolean ready,
            String detail) {
        return check(
                key,
                label,
                ready,
                ready
                        ? ProjectProductReadinessProjection.ReadinessLevel.CONNECTIVITY_VERIFIED
                        : ProjectProductReadinessProjection.ReadinessLevel.NOT_CONFIGURED,
                detail);
    }

    private ProjectProductReadinessProjection.Check check(
            String key,
            String label,
            boolean ready,
            ProjectProductReadinessProjection.ReadinessLevel level,
            String detail) {
        return new ProjectProductReadinessProjection.Check(key, label, ready, level, detail);
    }

    private boolean modelAvailable() {
        try {
            return modelReadinessPort.available();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private CapabilityDependencyReadiness dependency(CapabilityReadinessSnapshot snapshot, String name) {
        return snapshot.dependencies().stream()
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElse(new CapabilityDependencyReadiness(name, false, "NOT_PROBED", false, false, java.util.Map.of()));
    }

    private boolean contains(List<String> values, String expected) {
        if (values == null) return false;
        return values.stream().map(this::normalize).anyMatch(expected::equals);
    }

    private String reason(CapabilityDependencyReadiness dependency) {
        String reason = dependency.reason();
        return reason == null || reason.isBlank() ? dependency.name() + " 未就绪" : reason;
    }

    private String reasons(List<String> values) {
        if (values == null || values.isEmpty()) return "未就绪";
        return String.join("、", values);
    }

    private String evidenceSummary(ProjectWorkspaceProjection workspace) {
        List<String> parts = new ArrayList<>();
        if (workspace.dataResourceCount() > 0) parts.add("数据源 " + workspace.dataResourceCount());
        if (workspace.sourceRepositoryCount() > 0) parts.add("代码源 " + workspace.sourceRepositoryCount());
        if (!workspace.knowledgeBaseIds().isEmpty()) parts.add("知识库 " + workspace.knowledgeBaseIds().size());
        return String.join(" · ", parts);
    }

    private ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact acceptanceFact(String projectId) {
        try {
            ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact fact =
                    emergencyStopAcceptancePort.acceptance(projectId);
            return fact == null
                    ? ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact.notValidated()
                    : fact;
        } catch (RuntimeException error) {
            String message = error.getMessage();
            if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
            if (message.length() > 240) message = message.substring(0, 240);
            return new ProjectEmergencyStopAcceptancePort.EmergencyStopAcceptanceFact(
                    false,
                    "急停环境验收事实读取失败，Readiness 保持 fail-closed：" + message);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
