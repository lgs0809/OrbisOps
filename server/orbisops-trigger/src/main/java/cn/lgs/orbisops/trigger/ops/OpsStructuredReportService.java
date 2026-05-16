package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.runtime.OpsToolExecutionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpsStructuredReportService {

    private final OpsToolExecutionPolicy toolExecutionPolicy;
    private final OpsMcpReportEvidenceReader mcpEvidence;

    public OpsStructuredReportService(OpsToolExecutionPolicy toolExecutionPolicy) {
        this(toolExecutionPolicy, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpsStructuredReportService(OpsToolExecutionPolicy toolExecutionPolicy, OpsMcpReportEvidenceReader mcpEvidence) {
        this.toolExecutionPolicy = toolExecutionPolicy;
        this.mcpEvidence = mcpEvidence;
    }

    public Map<String, Object> compose(OpsAnalysisResponseDTO response) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("analysisId", response.getAnalysisId());
        report.put("agentDefinitionId", response.getAgentDefinitionId());
        report.put("agentVersion", response.getAgentVersion());
        report.put("generatedAt", response.getGeneratedAt());
        report.put("severity", severity(response));
        report.put("summary", summary(response));
        Map<String, Object> dataSources = new LinkedHashMap<>();
        dataSources.put("elasticsearch", response.getElasticsearchStatus());
        dataSources.put("prometheus", response.getPrometheusStatus());
        dataSources.put("mysqlSlowSql", response.getMysqlSlowSqlStatus());
        report.put("dataSources", dataSources);
        report.put("metrics", response.getMetricSummary());
        report.put("logs", response.getLogSummary());
        report.put("slowSql", response.getSlowSqlSummary());
        report.put("insights", Optional.ofNullable(response.getInsights()).orElse(List.of()));
        List<Map<String, Object>> evidenceTrace = evidenceTrace(response);
        report.put("recommendedActions", recommendedActions(response, evidenceTrace));
        report.put("operationBoundary", toolExecutionPolicy.operationBoundary());
        report.put("humanApproval", humanApproval());
        report.put("executionSteps", Optional.ofNullable(response.getAgentExecutionSteps()).orElse(List.of()));
        report.put("decisionTrace", decisionTrace(response));
        Map<String, Object> runtimeOutcome = runtimeOutcome(response);
        if (!runtimeOutcome.isEmpty()) {
            report.put("verificationStatus", runtimeOutcome.get("verificationStatus"));
            report.put("abstained", runtimeOutcome.get("abstained"));
        }
        String changePackageBehavior = changePackageBehavior(response);
        if (StringUtils.hasText(changePackageBehavior)) {
            report.put("changePackageBehavior", changePackageBehavior);
        }
        report.put("evidenceTrace", evidenceTrace);
        report.put("diagnosis", diagnosis(response, evidenceTrace));
        report.put("ragSources", evidenceTrace.stream()
                .map(item -> item.get("text"))
                .limit(20)
                .toList());
        return report;
    }

    private List<Map<String, Object>> recommendedActions(OpsAnalysisResponseDTO response, List<Map<String, Object>> evidenceTrace) {
        List<String> evidenceIds = evidenceTrace.stream()
                .map(item -> String.valueOf(item.get("id")))
                .limit(8)
                .toList();
        List<Map<String, Object>> actions = new ArrayList<>();
        int index = 1;
        for (OpsAnalysisResponseDTO.InsightDTO insight : Optional.ofNullable(response.getInsights()).orElse(List.of())) {
            if (!StringUtils.hasText(insight.getSuggestion())) {
                continue;
            }
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("actionId", "action-" + index++);
            action.put("title", insight.getTitle());
            action.put("riskLevel", insight.getLevel());
            action.put("recommendation", insight.getSuggestion());
            action.put("supportingEvidenceIds", evidenceIds);
            action.put("executionState", "NOT_EXECUTED");
            action.put("approvalState", "PENDING_OPERATOR_REVIEW");
            action.put("executor", "HUMAN_OPERATOR");
            action.put("requiresManualApproval", true);
            actions.add(action);
        }
        return actions;
    }

    private Map<String, Object> humanApproval() {
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("requiredForMutatingActions", true);
        approval.put("status", "PENDING_OPERATOR_REVIEW");
        approval.put("aiExecutionMode", "ANALYSIS_ONLY");
        approval.put("allowedAutomaticActions", List.of("READ_ONLY_QUERY", "REPORT_GENERATION", "NOTIFICATION"));
        approval.put("blockedAutomaticActions", List.of("RESTART_SERVICE", "SCALE_RESOURCE", "CHANGE_CONFIG", "EXECUTE_SQL_MUTATION"));
        return approval;
    }

    private List<Map<String, Object>> evidenceTrace(OpsAnalysisResponseDTO response) {
        List<Map<String, Object>> authoritative = authoritativeEvidenceTrace(response);
        if (mcpEvidence != null) {
            var combined = new LinkedHashMap<Object, Map<String, Object>>();
            authoritative.forEach(item -> combined.put(item.get("resultId"), item));
            mcpEvidence.read(response).forEach(item -> combined.putIfAbsent(item.get("resultId"), item));
            authoritative = new ArrayList<>(combined.values());
        }
        if (!authoritative.isEmpty()) return authoritative;
        List<Map<String, Object>> evidenceTrace = new ArrayList<>();
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results = Optional.ofNullable(response.getInvestigationResults()).orElse(List.of());
        for (int resultIndex = 0; resultIndex < results.size(); resultIndex++) {
            OpsAnalysisResponseDTO.InvestigationResultDTO result = results.get(resultIndex);
            if (result == null) {
                continue;
            }
            String resultSource = source(result);
            if ("investigate".equalsIgnoreCase(resultSource) || "unknown".equalsIgnoreCase(resultSource)) {
                continue;
            }
            List<String> evidence = Optional.ofNullable(result.getEvidence()).orElse(List.of());
            for (int evidenceIndex = 0; evidenceIndex < evidence.size(); evidenceIndex++) {
                Map<String, Object> item = new LinkedHashMap<>();
                String evidenceText = evidence.get(evidenceIndex);
                String resultId = "result-" + source(result) + "-" + (resultIndex + 1);
                item.put("id", "ev-" + source(result) + "-" + (resultIndex + 1) + "-" + (evidenceIndex + 1));
                item.put("resultId", resultId);
                item.put("outputHash", sha256(evidenceText));
                item.put("source", source(result));
                item.put("agent", result.getAgent());
                item.put("status", result.getStatus());
                item.put("confidence", result.getConfidence());
                item.put("text", evidenceText);
                evidenceTrace.add(item);
            }
        }
        return evidenceTrace;
    }

    private List<Map<String, Object>> authoritativeEvidenceTrace(OpsAnalysisResponseDTO response) {
        List<Map<String, Object>> evidenceTrace = new ArrayList<>();
        int index = 1;
        for (OpsAnalysisResponseDTO.AgentExecutionStepDTO step : Optional.ofNullable(response.getAgentExecutionSteps()).orElse(List.of())) {
            if (step == null || !Boolean.TRUE.equals(step.getVerified())
                    || !StringUtils.hasText(step.getSourceType())
                    || !StringUtils.hasText(step.getResultId())
                    || !StringUtils.hasText(step.getOutputHash())) {
                continue;
            }
            String source = step.getSourceType().trim().toLowerCase(java.util.Locale.ROOT);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "ev-" + source + "-" + index + "-1");
            item.put("resultId", step.getResultId());
            item.put("evidenceId", step.getEvidenceId());
            item.put("outputHash", step.getOutputHash());
            item.put("source", source);
            item.put("agent", step.getAgent());
            item.put("status", step.getStatus());
            item.put("confidence", 1.0D);
            item.put("text", StringUtils.hasText(step.getSummary()) ? step.getSummary() : source + " authoritative datasource evidence");
            evidenceTrace.add(item);
            index++;
        }
        return evidenceTrace;
    }

    private Map<String, Object> diagnosis(OpsAnalysisResponseDTO response, List<Map<String, Object>> evidenceTrace) {
        List<Map<String, Object>> facts = evidenceTrace.stream().map(item -> {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("evidenceRef", item.get("id"));
            ref.put("resultId", item.get("resultId"));
            ref.put("outputHash", item.get("outputHash"));
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("factId", "fact-" + item.get("id"));
            fact.put("statement", item.get("text"));
            fact.put("evidenceRefs", List.of(ref));
            return fact;
        }).toList();

        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results = Optional.ofNullable(response.getInvestigationResults()).orElse(List.of())
                .stream().filter(java.util.Objects::nonNull).toList();
        List<String> unknowns = new ArrayList<>();
        for (OpsAnalysisResponseDTO.InvestigationResultDTO result : results) {
            Optional.ofNullable(result.getGaps()).orElse(List.of()).stream()
                    .filter(StringUtils::hasText).forEach(unknowns::add);
            if (List.of("INSUFFICIENT", "BLOCKED", "ERROR").contains(text(result.getStatus()).toUpperCase())) {
                String detail = StringUtils.hasText(result.getSummary())
                        ? result.getSummary().trim()
                        : source(result) + " 未获得足够证据";
                unknowns.add(detail);
            }
        }
        boolean receiptOnly = evidenceTrace.stream().anyMatch(item -> Boolean.TRUE.equals(item.get("receiptOnly")));
        if (results.isEmpty() && facts.isEmpty()) unknowns.add("尚未形成数据源查询结果，不能判断系统正常。");
        if (receiptOnly) unknowns.add("已保留 MCP 查询回执；业务覆盖范围和恢复状态仍以运行报告为准。");

        List<String> recommendations = Optional.ofNullable(response.getInsights()).orElse(List.of()).stream()
                .map(OpsAnalysisResponseDTO.InsightDTO::getSuggestion)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("summary", summary(response));
        diagnosis.put("impact", List.of());
        diagnosis.put("facts", facts);
        diagnosis.put("inferences", List.of());
        diagnosis.put("excludedHypotheses", List.of());
        diagnosis.put("unknowns", unknowns.stream().distinct().toList());
        diagnosis.put("recommendations", recommendations);
        diagnosis.put("sourceStatus", sourceStatus(response, results, evidenceTrace));
        diagnosis.put("evidenceCompleteness", receiptOnly && results.isEmpty() ? "PARTIAL" : evidenceCompleteness(results, facts));
        diagnosis.put("confidence", confidence(results));
        Map<String, Object> runtimeOutcome = runtimeOutcome(response);
        Object runtimeRequiresAction = runtimeOutcome.get("requiresAction");
        boolean requiresAction = runtimeRequiresAction instanceof Boolean bool
                ? bool
                : response.getInvestigationPlan() != null
                && Boolean.TRUE.equals(response.getInvestigationPlan().getChangeRequested());
        diagnosis.put("requiresAction", requiresAction);
        diagnosis.put("suggestedNextAction", !recommendations.isEmpty()
                ? recommendations.get(0)
                : ("COMPLETE".equals(diagnosis.get("evidenceCompleteness")) ? null : "补充缺失数据源证据后再判断"));
        return diagnosis;
    }

    private List<Map<String, Object>> sourceStatus(
            OpsAnalysisResponseDTO response,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            List<Map<String, Object>> evidenceTrace) {
        Map<String, OpsAnalysisResponseDTO.DataSourceStatusDTO> configured = new LinkedHashMap<>();
        configured.put("elasticsearch", response.getElasticsearchStatus());
        configured.put("prometheus", response.getPrometheusStatus());
        configured.put("mysql_slow_sql", response.getMysqlSlowSqlStatus());
        Map<String, OpsAnalysisResponseDTO.InvestigationResultDTO> queried = new LinkedHashMap<>();
        for (OpsAnalysisResponseDTO.InvestigationResultDTO result : results) {
            String source = source(result);
            if ("investigate".equalsIgnoreCase(source) || "unknown".equalsIgnoreCase(source)) continue;
            queried.putIfAbsent(source, result);
        }
        java.util.LinkedHashSet<String> authoritative = Optional.ofNullable(response.getAgentExecutionSteps()).orElse(List.of()).stream()
                .filter(java.util.Objects::nonNull)
                .filter(step -> Boolean.TRUE.equals(step.getVerified()) && StringUtils.hasText(step.getSourceType()))
                .map(step -> step.getSourceType().trim().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        java.util.LinkedHashSet<String> sourceIds = new java.util.LinkedHashSet<>(configured.keySet());
        sourceIds.addAll(queried.keySet());
        sourceIds.addAll(authoritative);
        Map<String, Map<String, Object>> receipts = new LinkedHashMap<>();
        evidenceTrace.stream().filter(item -> Boolean.TRUE.equals(item.get("receiptOnly")))
                .forEach(item -> receipts.put(String.valueOf(item.get("source")), item));
        sourceIds.addAll(receipts.keySet());
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (String sourceId : sourceIds) {
            OpsAnalysisResponseDTO.DataSourceStatusDTO health = configured.get(sourceId);
            OpsAnalysisResponseDTO.InvestigationResultDTO result = queried.get(sourceId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceId", sourceId);
            item.put("sourceName", health != null && StringUtils.hasText(health.getName()) ? health.getName() : sourceId);
            if (receipts.containsKey(sourceId)) {
                item.put("sourceName", receipts.get(sourceId).get("sourceName"));
                item.put("queryStatus", "SUCCEEDED");
                item.put("assessment", "UNKNOWN");
                item.put("state", "UNKNOWN");
                item.put("detail", receipts.get(sourceId).get("text"));
            } else if (health != null && Boolean.FALSE.equals(health.getAvailable())) {
                item.put("queryStatus", "FAILED");
                item.put("assessment", "UNKNOWN");
                item.put("state", "UNAVAILABLE");
                item.put("detail", text(health.getMessage()));
            } else if (result == null && authoritative.contains(sourceId)) {
                item.put("queryStatus", "SUCCEEDED");
                item.put("assessment", "UNKNOWN");
                item.put("state", "UNKNOWN");
                item.put("detail", "已获得 authoritative datasource evidence");
            } else if (result == null) {
                boolean notConfigured = health == null || health.getAvailable() == null;
                item.put("queryStatus", notConfigured ? "NOT_CONFIGURED" : "NOT_QUERIED");
                item.put("assessment", "UNKNOWN");
                item.put("state", notConfigured ? "NOT_CONFIGURED" : "NOT_QUERIED");
                item.put("detail", notConfigured ? "未配置或未暴露该数据源" : "数据源可用，但本次调查未查询");
            } else {
                String resultStatus = text(result.getStatus()).toUpperCase();
                String queryStatus = switch (resultStatus) {
                    case "FOUND", "NOT_FOUND" -> "SUCCEEDED";
                    case "INSUFFICIENT", "BLOCKED" -> "INSUFFICIENT";
                    case "ERROR" -> "FAILED";
                    default -> "INSUFFICIENT";
                };
                item.put("queryStatus", queryStatus);
                // Investigation retrieval status only says whether the query found matching
                // evidence. It is not itself a health verdict, so never infer NORMAL or
                // ABNORMAL here without an explicit business assessment fact.
                item.put("assessment", "UNKNOWN");
                item.put("state", "SUCCEEDED".equals(queryStatus) ? "UNKNOWN" : "UNAVAILABLE");
                item.put("detail", text(result.getSummary()));
            }
            statuses.add(item);
        }
        return statuses;
    }

    private String evidenceCompleteness(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            List<Map<String, Object>> facts) {
        // A narrative Agent result is not itself evidence. Without at least one
        // grounded evidence fact the formal diagnosis must remain insufficient.
        if (facts == null || facts.isEmpty()) return "INSUFFICIENT";
        if (results == null || results.isEmpty()) return "COMPLETE";
        boolean hasGap = results.stream()
                .filter(java.util.Objects::nonNull)
                .filter(result -> !"investigate".equalsIgnoreCase(source(result)))
                .anyMatch(result -> List.of("INSUFFICIENT", "BLOCKED", "ERROR")
                        .contains(text(result.getStatus()).toUpperCase()));
        return hasGap ? "PARTIAL" : "COMPLETE";
    }

    private String confidence(List<OpsAnalysisResponseDTO.InvestigationResultDTO> results) {
        double average = results.stream().map(OpsAnalysisResponseDTO.InvestigationResultDTO::getConfidence)
                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D);
        if (average >= 0.8D) return "HIGH";
        if (average >= 0.5D) return "MEDIUM";
        return "LOW";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private List<Map<String, Object>> decisionTrace(OpsAnalysisResponseDTO response) {
        List<Map<String, Object>> trace = new ArrayList<>();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = response.getInvestigationPlan();
        if (plan != null) {
            Map<String, Object> planning = new LinkedHashMap<>();
            planning.put("stage", "PLAN");
            planning.put("intent", plan.getIntent());
            planning.put("reason", plan.getReason());
            planning.put("tasks", Optional.ofNullable(plan.getTasks()).orElse(List.of()));
            planning.put("conditionalTasks", Optional.ofNullable(plan.getConditionalTasks()).orElse(List.of()));
            planning.put("skippedTasks", Optional.ofNullable(plan.getSkippedTasks()).orElse(List.of()));
            trace.add(planning);
        }
        for (OpsAnalysisResponseDTO.AgentExecutionStepDTO step : Optional.ofNullable(response.getAgentExecutionSteps()).orElse(List.of())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", "NODE");
            item.put("nodeId", step.getNodeId());
            item.put("nodeType", step.getNodeType());
            item.put("agent", step.getAgent());
            item.put("source", step.getSource());
            item.put("status", step.getStatus());
            item.put("summary", step.getSummary());
            item.put("durationMs", step.getDurationMs());
            trace.add(item);
        }
        for (OpsAnalysisResponseDTO.InvestigationResultDTO result : Optional.ofNullable(response.getInvestigationResults()).orElse(List.of())) {
            List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts = result == null ? List.of() : Optional.ofNullable(result.getAttempts()).orElse(List.of());
            for (OpsAnalysisResponseDTO.InvestigationAttemptDTO attempt : attempts) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("stage", "SUB_AGENT_ATTEMPT");
                item.put("source", source(result));
                item.put("agent", result.getAgent());
                item.put("query", attempt.getQuery());
                item.put("resultCount", attempt.getResultCount());
                item.put("reason", attempt.getReason());
                trace.add(item);
            }
        }
        return trace;
    }

    private Map<String, Object> runtimeOutcome(OpsAnalysisResponseDTO response) {
        List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps = Optional.ofNullable(response.getAgentExecutionSteps()).orElse(List.of());
        for (int index = steps.size() - 1; index >= 0; index--) {
            OpsAnalysisResponseDTO.AgentExecutionStepDTO step = steps.get(index);
            if (step == null
                    || !("REACT_OUTCOME".equals(step.getEventType())
                    || "WORKFLOW_OUTCOME".equals(step.getEventType()))) continue;
            if (step.getOutcome() != null && !step.getOutcome().isEmpty()) {
                return step.getOutcome();
            }
        }
        return Map.of();
    }

    private String changePackageBehavior(OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getAgentExecutionSteps()).orElse(List.of()).stream()
                .filter(java.util.Objects::nonNull)
                .filter(step -> "CHANGE_PACKAGE_PREPARED".equals(step.getEventType()))
                .map(OpsAnalysisResponseDTO.AgentExecutionStepDTO::getChangePackageBehavior)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String source(OpsAnalysisResponseDTO.InvestigationResultDTO result) {
        return StringUtils.hasText(result.getSource()) ? result.getSource() : "unknown";
    }

    private String severity(OpsAnalysisResponseDTO response) {
        if (hasEvidenceGap(response)) {
            return "WARN";
        }
        if (response.getInsights() == null || response.getInsights().isEmpty()) {
            return "INFO";
        }
        if (response.getInsights().stream().anyMatch(item -> "CRITICAL".equalsIgnoreCase(item.getLevel()) || "ERROR".equalsIgnoreCase(item.getLevel()))) {
            return "ERROR";
        }
        if (response.getInsights().stream().anyMatch(item -> "WARN".equalsIgnoreCase(item.getLevel()))) {
            return "WARN";
        }
        return "INFO";
    }

    private String summary(OpsAnalysisResponseDTO response) {
        if (StringUtils.hasText(response.getMarkdownReport())) {
            String text = response.getMarkdownReport().replaceAll("#+", "").trim();
            return text.length() > 300 ? text.substring(0, 300) + "..." : text;
        }
        if (response.getInsights() != null && !response.getInsights().isEmpty()) {
            return response.getInsights().get(0).getTitle();
        }
        if (hasEvidenceGap(response)) {
            return "本次分析未获得足够的实时证据，不能据此判断系统正常。";
        }
        return "本次分析未发现明确异常。";
    }

    private boolean hasEvidenceGap(OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getInvestigationResults()).orElse(List.of()).stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsAnalysisResponseDTO.InvestigationResultDTO::getStatus)
                .filter(StringUtils::hasText)
                .anyMatch(status -> "INSUFFICIENT".equalsIgnoreCase(status)
                        || "BLOCKED".equalsIgnoreCase(status)
                        || "ERROR".equalsIgnoreCase(status));
    }
}
