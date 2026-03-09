package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectWorkspaceReadinessRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntSupplier;

public final class ProjectWorkspaceProjectionApplicationService {

    private static final Set<String> DISABLED_STATUSES =
            Set.of("DISABLED", "REJECTED", "DELETED", "OFFLINE");

    private final IProjectWorkspaceReadinessRepository readinessRepository;
    private final ProjectDefaultAgentPublicationPort defaultAgentPublicationPort;
    private final ProjectWorkspaceReadinessFailurePort failurePort;
    private final ProjectFirstValueEvidencePort firstValueEvidencePort;

    public ProjectWorkspaceProjectionApplicationService(
            IProjectWorkspaceReadinessRepository readinessRepository,
            ProjectDefaultAgentPublicationPort defaultAgentPublicationPort,
            ProjectWorkspaceReadinessFailurePort failurePort) {
        this(readinessRepository, defaultAgentPublicationPort, failurePort,
                projectId -> ProjectFirstValueEvidencePort.ProjectFirstValueEvidence.none());
    }

    public ProjectWorkspaceProjectionApplicationService(
            IProjectWorkspaceReadinessRepository readinessRepository,
            ProjectDefaultAgentPublicationPort defaultAgentPublicationPort,
            ProjectWorkspaceReadinessFailurePort failurePort,
            ProjectFirstValueEvidencePort firstValueEvidencePort) {
        if (readinessRepository == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_READINESS_REPOSITORY_REQUIRED");
        }
        if (defaultAgentPublicationPort == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_PUBLICATION_PORT_REQUIRED");
        }
        if (failurePort == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_READINESS_FAILURE_PORT_REQUIRED");
        }
        if (firstValueEvidencePort == null) {
            throw new IllegalArgumentException("PROJECT_FIRST_VALUE_EVIDENCE_PORT_REQUIRED");
        }
        this.readinessRepository = readinessRepository;
        this.defaultAgentPublicationPort = defaultAgentPublicationPort;
        this.failurePort = failurePort;
        this.firstValueEvidencePort = firstValueEvidencePort;
    }

    public boolean persistenceAvailable() {
        return readinessRepository.available();
    }

    public ProjectWorkspaceProjection project(ProjectWorkspaceProjectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECTION_REQUEST_REQUIRED");
        }
        ProjectWorkspaceProjectionRequest.Project project = request.project();
        if (project.projectId().isBlank()) {
            throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        }

        ResourceReadiness readiness = resourceReadiness(project.projectId(), request.dataResourceCount());
        boolean knowledgeConfigured = !request.knowledgeBaseIds().isEmpty();
        boolean agentConfigured = !project.defaultAgentId().isBlank();
        boolean defaultAgentPublished = agentConfigured
                && publishedProjectAgentExists(project.projectId(), project.defaultAgentId());
        List<ProjectWorkspaceProjection.DiagnosticScenario> scenarios = diagnosticScenarios(
                defaultAgentPublished,
                request.evidenceSources(),
                knowledgeConfigured,
                readiness.sourceRepositoryCount());
        boolean readyForInvestigation = scenarios.stream()
                .anyMatch(ProjectWorkspaceProjection.DiagnosticScenario::ready);
        String readinessReason = !agentConfigured
                ? "DEFAULT_AGENT_NOT_CONFIGURED"
                : !defaultAgentPublished
                ? "DEFAULT_AGENT_NOT_PUBLISHED"
                : !readyForInvestigation
                ? "NO_RESOURCE_CONNECTED"
                : "READY";
        String recommendedScenarioId = scenarios.stream()
                .filter(ProjectWorkspaceProjection.DiagnosticScenario::ready)
                .map(ProjectWorkspaceProjection.DiagnosticScenario::scenarioId)
                .findFirst()
                .orElse("");
        boolean liveEvidenceConfigured = readiness.dataResourceCount() > 0;
        ProjectFirstValueEvidencePort.ProjectFirstValueEvidence firstValue =
                firstValueEvidencePort.verification(project.projectId());
        List<ProjectWorkspaceProjection.OnboardingStep> onboarding = List.of(
                onboarding("project", "创建 Project", true, false),
                onboarding("evidence", "接入实时数据源", liveEvidenceConfigured, false),
                onboarding("query-proof", "完成一次真实查询验证", firstValue.verified(), false),
                onboarding("agent", "默认智能助手可用", defaultAgentPublished, false));

        return new ProjectWorkspaceProjection(
                project,
                request.projectSkills(),
                request.enabledGlobalSkills(),
                request.knowledgeBaseIds(),
                request.projectKnowledgeBases(),
                request.enabledGlobalKnowledgeBases(),
                readiness.dataResourceCount(),
                readiness.sourceRepositoryCount(),
                readiness.executionResourceCount(),
                readiness.totalCount(),
                request.generatedMcpCount(),
                defaultAgentPublished,
                readyForInvestigation,
                readinessReason,
                scenarios,
                recommendedScenarioId,
                onboarding);
    }

    private ResourceReadiness resourceReadiness(String projectId, int dataResourceCount) {
        if (!readinessRepository.available()) {
            return new ResourceReadiness(dataResourceCount, 0, 0);
        }
        int sourceRepositoryCount = readinessCount(
                projectId,
                "source repository",
                () -> readinessRepository.countReadySourceRepositories(projectId));
        int executionResourceCount = readinessCount(
                projectId,
                "execution resource",
                () -> readinessRepository.countEnabledExecutionResources(projectId));
        return new ResourceReadiness(dataResourceCount, sourceRepositoryCount, executionResourceCount);
    }

    private int readinessCount(String projectId, String catalog, IntSupplier counter) {
        try {
            return Math.max(counter.getAsInt(), 0);
        } catch (RuntimeException error) {
            failurePort.readinessQueryFailed(catalog, projectId, error);
            return 0;
        }
    }

    private boolean publishedProjectAgentExists(String projectId, String agentId) {
        return defaultAgentPublicationPort.published(projectId, agentId);
    }

    private List<ProjectWorkspaceProjection.DiagnosticScenario> diagnosticScenarios(
            boolean defaultAgentPublished,
            List<ProjectWorkspaceProjectionRequest.EvidenceSource> evidenceSources,
            boolean knowledgeConfigured,
            int sourceRepositoryCount) {
        Set<String> types = new LinkedHashSet<>();
        for (ProjectWorkspaceProjectionRequest.EvidenceSource source : evidenceSources) {
            if (!disabledStatus(source.status())) {
                String type = normalizeType(source.type());
                if (!type.isBlank()) {
                    types.add(type);
                }
            }
        }

        boolean logs = containsAny(types, "elasticsearch", "opensearch", "local_log", "log", "logs");
        boolean metrics = containsAny(types, "prometheus", "grafana", "metrics");
        boolean database = containsAny(types, "mysql", "postgresql", "postgres", "database");
        boolean code = sourceRepositoryCount > 0;
        boolean liveEvidence = !types.isEmpty();

        List<ProjectWorkspaceProjection.DiagnosticScenario> scenarios = new ArrayList<>();
        scenarios.add(scenario("GENERAL_DIAGNOSIS", "综合故障诊断",
                "根据当前项目已接入的实时数据源自动选择证据，不会把知识库或代码仓库单独当作当前生产状态证明。",
                "请对当前项目进行综合故障诊断。先说明需要的时间范围和现象，再查询真实证据；区分事实、推断、已排除项和未知项。证据不足时不要生成变更方案。",
                defaultAgentPublished && liveEvidence,
                defaultAgentPublished ? "至少接入一种实时日志、指标或数据库数据源" : "先发布项目默认 Agent",
                List.of()));
        scenarios.add(scenario("LOG_ANALYSIS", "日志异常分析",
                "查询限定时间范围内的错误日志和代表性样本。",
                "请分析当前项目最近十分钟的错误日志。先确认日志索引或日志源，只返回真实命中证据，并总结主要错误模式和仍缺少的信息。",
                defaultAgentPublished && logs,
                defaultAgentPublished ? "需接入 Elasticsearch、OpenSearch 或本地日志" : "先发布项目默认 Agent",
                List.of("ELASTICSEARCH")));
        scenarios.add(scenario("METRIC_ANALYSIS", "指标异常分析",
                "查询受控时间范围内的指标趋势、异常点和相关标签。",
                "请分析当前项目最近三十分钟的关键指标异常。使用真实 Prometheus 或 Grafana 数据，说明时间窗口、指标值、趋势和证据缺口。",
                defaultAgentPublished && metrics,
                defaultAgentPublished ? "需接入 Prometheus 或 Grafana" : "先发布项目默认 Agent",
                List.of("PROMETHEUS")));
        scenarios.add(scenario("SLOW_SQL_ANALYSIS", "慢 SQL 分析",
                "只读查询慢 SQL、执行计划和相关数据库证据。",
                "请只读分析当前项目最近的慢 SQL。先查询真实慢 SQL 样本，再对可安全分析的 SELECT 执行 EXPLAIN；禁止执行写 SQL。",
                defaultAgentPublished && database,
                defaultAgentPublished ? "需接入只读 MySQL 或 PostgreSQL" : "先发布项目默认 Agent",
                List.of("MYSQL_SLOW_SQL")));
        scenarios.add(scenario("KNOWLEDGE_QA", "项目知识查询",
                "从项目已绑定知识库中查找约定、历史方案和业务说明。",
                "请基于当前项目知识库回答问题，并明确列出引用来源。知识库没有覆盖时直接说明，不要把推断写成项目事实。",
                defaultAgentPublished && knowledgeConfigured,
                defaultAgentPublished ? "需绑定至少一个项目知识库" : "先发布项目默认 Agent",
                List.of("RAG")));
        scenarios.add(scenario("CODE_INVESTIGATION", "代码问题定位",
                "在已登记代码仓库中只读搜索调用链和候选影响范围。",
                "请在当前项目已登记的代码仓库中定位问题。先用 Read、Grep、Glob 或 LSP 获取真实代码证据，给出文件和行号；证据不足时不要修改代码。",
                defaultAgentPublished && code,
                defaultAgentPublished ? "需登记并启用代码仓库" : "先发布项目默认 Agent",
                List.of("CODE")));
        return List.copyOf(scenarios);
    }

    private ProjectWorkspaceProjection.DiagnosticScenario scenario(
            String scenarioId,
            String name,
            String description,
            String promptTemplate,
            boolean ready,
            String unavailableReason,
            List<String> allowedSources) {
        return new ProjectWorkspaceProjection.DiagnosticScenario(
                scenarioId,
                name,
                description,
                promptTemplate,
                ready,
                unavailableReason,
                allowedSources);
    }

    private ProjectWorkspaceProjection.OnboardingStep onboarding(
            String key,
            String label,
            boolean completed,
            boolean optional) {
        return new ProjectWorkspaceProjection.OnboardingStep(key, label, completed, optional);
    }

    private boolean containsAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean disabledStatus(String status) {
        return DISABLED_STATUSES.contains(value(status).toUpperCase(Locale.ROOT));
    }

    private String normalizeType(String type) {
        String value = value(type);
        if (value.isBlank()) {
            value = "mysql";
        }
        value = value.toLowerCase(Locale.ROOT).replace("-", "_");
        if ("pgsql".equals(value) || "pg".equals(value)) {
            return "postgresql";
        }
        if ("elk".equals(value) || "es".equals(value)) {
            return "elasticsearch";
        }
        if ("k8s".equals(value)) {
            return "kubernetes";
        }
        if ("gitlab".equals(value)) {
            return "gitlab_ci";
        }
        if ("http".equals(value) || "api".equals(value)) {
            return "http_api";
        }
        return value;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private record ResourceReadiness(
            int dataResourceCount,
            int sourceRepositoryCount,
            int executionResourceCount
    ) {
        private ResourceReadiness {
            dataResourceCount = Math.max(dataResourceCount, 0);
            sourceRepositoryCount = Math.max(sourceRepositoryCount, 0);
            executionResourceCount = Math.max(executionResourceCount, 0);
        }

        private int totalCount() {
            return dataResourceCount + sourceRepositoryCount + executionResourceCount;
        }
    }
}
