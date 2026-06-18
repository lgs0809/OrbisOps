package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.changepackage.ChangePackageProductMetricsProjection;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.domain.incident.model.IncidentProductMetricsProjection;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Product metrics backed by authoritative Incident/Timeline events, ChangePackage facts,
 * and ProjectWorkspace onboarding/readiness projections. The service intentionally avoids
 * bounded detail scans and client-side click inference: metrics that do not yet have a
 * durable or authoritative projection stay explicitly uncollected.
 */
@Service
public class OpsProductMetricsService {

    private final IncidentQueryApplicationService incidents;
    private final ChangePackageQueryService changePackages;
    private final OpsProjectWorkspaceService projects;

    public OpsProductMetricsService(
            IncidentQueryApplicationService incidents,
            ChangePackageQueryService changePackages,
            OpsProjectWorkspaceService projects) {
        this.incidents = incidents;
        this.changePackages = changePackages;
        this.projects = projects;
    }

    public Map<String, Object> snapshot() {
        String since = LocalDateTime.now(ZoneId.systemDefault())
                .minusDays(7)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        IncidentProductMetricsProjection projection = incidents.productMetrics(since);
        ChangePackageProductMetricsProjection changeProjection = changePackages.productMetrics();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sample", Map.of(
                "incidentCount", projection.incidentCount(),
                "timelineEventCount", projection.timelineEventCount(),
                "scope", "authoritative-incident-event-projection",
                "boundedScan", false));
        root.put("northStar", Map.of(
                "weeklyHelpfulResolvedIncidents", projection.weeklyHelpfulResolvedIncidents(),
                "definition", "最近 7 天当前 occurrence 内恢复验证成功且由用户明确确认有帮助的 Incident 数",
                "events", java.util.List.of("VERIFICATION_SUCCEEDED", "USER_CONFIRMED_HELPFUL")));
        root.put("activation", activation());
        root.put("firstValue", firstValue());
        root.put("diagnosis", diagnosis(projection));
        root.put("tool", toolMetrics());
        root.put("remediation", remediation(projection, changeProjection));
        root.put("business", business(projection));
        root.put("collaboration", Map.of(
                "commentCount", projection.commentCount(),
                "source", "COMMENT Incident timeline events"));
        return root;
    }

    private Map<String, Object> activation() {
        Map<String, Object> snapshot = projects == null ? Map.of() : projects.snapshot();
        List<Map<String, Object>> projectViews = maps(snapshot.get("projects"));
        long evidenceConnected = 0L;
        long queryProofVerified = 0L;
        long defaultAgentReady = 0L;
        long onboardingComplete = 0L;
        long diagnosisReady = 0L;

        for (Map<String, Object> project : projectViews) {
            List<Map<String, Object>> onboarding = maps(project.get("onboarding"));
            if (stepCompleted(onboarding, "evidence")) evidenceConnected++;
            if (stepCompleted(onboarding, "query-proof")) queryProofVerified++;
            if (stepCompleted(onboarding, "agent")) defaultAgentReady++;
            boolean requiredComplete = !onboarding.isEmpty() && onboarding.stream()
                    .filter(step -> !bool(step.get("optional")))
                    .allMatch(step -> bool(step.get("completed")));
            if (requiredComplete) onboardingComplete++;
            Map<String, Object> readiness = map(project.get("diagnosisReadiness"));
            if (bool(readiness.get("ready"))) diagnosisReady++;
        }

        long projectCount = projectViews.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectCount", projectCount);
        result.put("evidenceConnectedProjects", evidenceConnected);
        result.put("queryProofVerifiedProjects", queryProofVerified);
        result.put("defaultAgentReadyProjects", defaultAgentReady);
        result.put("onboardingCompleteProjects", onboardingComplete);
        result.put("activatedProjects", onboardingComplete);
        result.put("diagnosisReadyProjects", diagnosisReady);
        result.put("activationRate", rate(onboardingComplete, projectCount));
        result.put("definition", "Activated = all required Project onboarding steps complete: project + live evidence + real query proof + published default Agent");
        result.put("source", "authoritative ProjectWorkspace onboarding/readiness projection");
        result.put("highCardinalityLabels", false);
        return result;
    }

    private Map<String, Object> firstValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ttfvMs", uncollected("需要持久化 Project 创建时间到首个 Evidence 的统一产品事件"));
        result.put("projectToFirstDiagnosisMs", uncollected("需要首个结构化 Diagnosis 落库时间"));
        result.put("onboardingCompletion", Map.of(
                "status", "COLLECTED_AT_PROJECT_READINESS",
                "source", "diagnosisReadiness/onboarding"));
        return result;
    }

    private Map<String, Object> diagnosis(IncidentProductMetricsProjection projection) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mttaMs", metric(projection.averageMttaMs()));
        result.put("diagnosisDurationMs", metric(projection.averageDiagnosisDurationMs()));
        result.put("evidenceInsufficientRate", uncollected("需要结构化 Diagnosis 产品事件投影，不能从自由文本或 Incident 状态推断"));
        result.put("evidenceCompleteness", Map.of(
                "status", "AVAILABLE_IN_STRUCTURED_DIAGNOSIS",
                "source", "DiagnosisResult.evidenceCompleteness"));
        result.put("userCorrectionRate", uncollected("需要明确的 Diagnosis correction 产品事件"));
        result.put("firstResolutionRate", uncollected("需要重复故障 occurrence 解决结果投影"));
        return result;
    }

    private Map<String, Object> toolMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolCallsPerIncident", uncollected("需要 ToolExecution → Incident 的产品事件投影"));
        result.put("mcpSuccessRate", Map.of("status", "AVAILABLE_IN_RUNTIME_SLO", "source", "mcpRuntimeSlo"));
        result.put("invalidToolCalls", uncollected("需要统一 ToolExecution 无效调用分类事件"));
        result.put("tokenCost", uncollected("模型 token/cost 尚未统一归一到 Incident 维度"));
        result.put("p50P95Duration", Map.of("status", "AVAILABLE_IN_RUNTIME_TELEMETRY", "source", "Micrometer runtime/tool timers"));
        return result;
    }

    private Map<String, Object> remediation(
            IncidentProductMetricsProjection projection,
            ChangePackageProductMetricsProjection changes) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changePackageGenerationRate", rate(changes.incidentLinkedPackageCount(), projection.incidentCount()));
        result.put("approvalRate", rate(changes.approvedCount(), changes.packageCount()));
        result.put("landedRate", rate(changes.landedCount(), changes.approvedCount()));
        result.put("landingFailedRate", rate(changes.landingFailedCount(), changes.approvedCount()));
        result.put("needsReplanRate", rate(changes.needsReplanCount(), changes.approvedCount()));
        result.put("rejectedRate", rate(changes.rejectedCount(), changes.packageCount()));
        result.put("packageCount", changes.packageCount());
        result.put("reconciliationRate", uncollected("需要 ToolExecution reconciliation → Incident 产品事件投影"));
        result.put("manualIntervention", changes.landingFailedCount() + changes.needsReplanCount() + changes.rejectedCount());
        result.put("rollback", uncollected("需要统一 ROLLBACK_COMPLETED 产品事件"));
        result.put("verificationSuccess", projection.verificationSuccessCount());
        return result;
    }

    private Map<String, Object> business(IncidentProductMetricsProjection projection) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mttrMs", metric(projection.averageMttrMs()));
        result.put("manualOperationSteps", uncollected("需要将人工操作统一记录为 Incident product event"));
        result.put("nightManualIntervention", uncollected("人工介入事件尚未携带统一 actor/time classification"));
        result.put("repeatIncidentHandlingTime", uncollected("需要按 recurring/fingerprint occurrence 建立聚合投影"));
        return result;
    }

    private Object metric(Long value) {
        return value == null ? Map.of("status", "NO_SAMPLES") : value;
    }

    private Object rate(long numerator, long denominator) {
        if (denominator <= 0) return Map.of("status", "NO_SAMPLES");
        return Math.round((numerator * 10000.0d) / denominator) / 10000.0d;
    }

    private boolean stepCompleted(List<Map<String, Object>> onboarding, String key) {
        return onboarding.stream()
                .anyMatch(step -> key.equals(String.valueOf(step.getOrDefault("key", "")))
                        && bool(step.get("completed")));
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream()
                .filter(Map.class::isInstance)
                .map(this::map)
                .toList();
    }

    private Map<String, Object> uncollected(String reason) {
        return Map.of("status", "NOT_COLLECTED", "reason", reason);
    }
}
