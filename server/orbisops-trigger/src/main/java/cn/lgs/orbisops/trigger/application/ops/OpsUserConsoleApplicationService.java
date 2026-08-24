package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.project.ProjectCatalogEntry;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.analysis.service.AnalysisTaskPresentationPolicy;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OpsUserConsoleApplicationService {

    private final AuthorizeProjectAccessUseCase projectAccessUseCase;
    private final OpsChatApplicationService chatApplicationService;
    private final IncidentQueryApplicationService incidentQueries;
    private final ChangePackageQueryService changePackageQueries;
    private final OpsChangePackagePermissionService changePackagePermissionService;
    private final OpsConfigAuditService configAuditService;
    private final WorkSessionRunApplicationService workSessionRuns;

    public OpsUserConsoleApplicationService(AuthorizeProjectAccessUseCase projectAccessUseCase,
                                            OpsChatApplicationService chatApplicationService,
                                            IncidentQueryApplicationService incidentQueries,
                                            ChangePackageQueryService changePackageQueries,
                                            OpsChangePackagePermissionService changePackagePermissionService,
                                            OpsConfigAuditService configAuditService,
                                            WorkSessionRunApplicationService workSessionRuns) {
        this.projectAccessUseCase = projectAccessUseCase;
        this.chatApplicationService = chatApplicationService;
        this.incidentQueries = incidentQueries;
        this.changePackageQueries = changePackageQueries;
        this.changePackagePermissionService = changePackagePermissionService;
        this.configAuditService = configAuditService;
        this.workSessionRuns = workSessionRuns;
    }

    public Map<String, Object> dashboardOverview(String username, String userId) {
        List<ProjectCatalogEntry> projects = authorizedProjects(username, userId);
        Set<String> projectIds = projects.stream().map(ProjectCatalogEntry::projectId).collect(Collectors.toSet());
        List<OpsChatSession> sessions = chatApplicationService.listSessions(
                null, null, null, null, 8, safeUserId(username, userId));
        List<Map<String, Object>> executions = myExecutions(username, userId, null, 50);
        List<Map<String, Object>> audits = myAudits(username, userId, 20);
        var incidents = projects.stream()
                .flatMap(project -> incidentQueries.list(project.projectId(), null, 50).stream())
                .toList();
        List<WorkSessionRunSnapshot> workflowDecisions = workSessionRuns
                .listByStatus(WorkSessionRunStatus.WAITING_APPROVAL, 200).stream()
                .filter(run -> projectIds.contains(run.projectId()))
                .filter(run -> canDecideWorkflow(run, username, userId))
                .limit(8)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentIncidents", incidents.stream()
                .filter(item -> item.status() != IncidentStatus.RESOLVED && item.status() != IncidentStatus.CLOSED)
                .limit(8)
                .toList());
        result.put("actionRequiredIncidents", incidents.stream()
                .filter(item -> item.status() == IncidentStatus.ACTION_REQUIRED)
                .filter(item -> !StringUtils.hasText(item.ownerUserId())
                        || principalKeys(username, userId).stream().anyMatch(key -> same(key, item.ownerUserId())))
                .limit(8)
                .toList());
        result.put("availableProjects", projectViews(projects, username, userId));
        result.put("recentSessions", sessions);
        result.put("pendingWorkflowDecisions", workflowDecisions.stream().map(this::workflowDecisionView).toList());
        List<Map<String, Object>> pendingApprovals = executions.stream()
                .filter(item -> ChangePackageStatus.REVIEWING.name().equals(item.get("status")))
                .filter(item -> Boolean.TRUE.equals(item.get("canApprove")))
                .toList();
        List<Map<String, Object>> pendingOperations = executions.stream()
                .filter(item -> ChangePackageStatus.APPROVED.name().equals(item.get("status"))
                        || ChangePackageStatus.LANDING_RUNNING.name().equals(item.get("status")))
                .filter(item -> Boolean.TRUE.equals(item.get("canVerify")))
                .toList();
        List<Map<String, Object>> actionableExecutions = java.util.stream.Stream
                .concat(pendingApprovals.stream(), pendingOperations.stream())
                .limit(8)
                .toList();
        result.put("pendingExecutions", actionableExecutions);
        result.put("pendingApprovals", pendingApprovals.stream().limit(8).toList());
        result.put("pendingOperations", pendingOperations.stream().limit(8).toList());
        result.put("workQueue", Map.of(
                "incidentActionCount", ((List<?>) result.get("actionRequiredIncidents")).size(),
                "approvalCount", pendingApprovals.size() + workflowDecisions.size(),
                "workflowDecisionCount", workflowDecisions.size(),
                "operationCount", pendingOperations.size(),
                "auditReminderCount", audits.size()));
        result.put("auditReminders", audits.stream().limit(8).toList());
        return result;
    }

    public List<Map<String, Object>> myExecutions(String username, String userId, String status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Set<String> authorizedProjects = authorizedProjects(username, userId).stream()
                .map(ProjectCatalogEntry::projectId)
                .collect(Collectors.toSet());
        if (authorizedProjects.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> packages = new ArrayList<>();
        for (String projectId : authorizedProjects) {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("projectId", projectId);
            if (StringUtils.hasText(status)) {
                query.put("status", status);
            }
            try {
                packages.addAll(changePackageQueries.list(
                        new ChangePackageListQuery(query, Math.max(safeLimit, 200))));
            } catch (RuntimeException ignored) {
                // A broken package store should not hide the rest of the user console.
            }
        }
        return packages.stream()
                .limit(safeLimit)
                .map(pkg -> packageExecutionSummary(pkg, username, userId))
                .toList();
    }

    private Map<String, Object> packageExecutionSummary(Map<String, Object> pkg, String username, String userId) {
        Map<String, Object> item = new LinkedHashMap<>();
        String packageId = text(firstNonNull(pkg.get("packageId"), pkg.get("package_id")));
        String projectId = text(firstNonNull(pkg.get("projectId"), pkg.get("project_id")));
        item.put("packageId", packageId);
        item.put("projectId", projectId);
        item.put("title", text(firstNonNull(pkg.get("objective"), pkg.get("summary"))));
        item.put("summary", text(pkg.get("summary")));
        item.put("status", text(pkg.get("status")));
        item.put("riskLevel", text(firstNonNull(pkg.get("riskLevel"), pkg.get("risk_level"))));
        item.put("version", pkg.getOrDefault("version", 0));
        item.put("proposalSource", "CHANGE_PACKAGE");
        item.put("actionCount", listSize(firstNonNull(pkg.get("mcpSteps"), pkg.get("mcp_steps_json"))));
        item.put("createdAt", firstNonNull(pkg.get("createTime"), pkg.get("create_time")));
        item.put("updatedAt", firstNonNull(pkg.get("updateTime"), pkg.get("update_time")));
        item.put("relation", "AUTHORIZED_PROJECT");
        item.put("canApprove", canPackageAction(projectId, username, userId, "APPROVE"));
        item.put("canReject", item.get("canApprove"));
        item.put("canVerify", canPackageAction(projectId, username, userId, "LAND"));
        return item;
    }

    private boolean canPackageAction(String projectId, String username, String userId, String action) {
        try {
            AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                    text(username), text(userId), "", AdminAuthService.SCOPE_USER, false);
            if ("LAND".equals(action)) {
                changePackagePermissionService.assertCanLandPackage(projectId, principal);
            } else {
                changePackagePermissionService.assertCanApprovePackage(projectId, principal);
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean canDecideWorkflow(WorkSessionRunSnapshot run, String username, String userId) {
        for (String actor : principalKeys(username, userId)) {
            try {
                workSessionRuns.resumeApproval(run.runId(), run.projectId(), actor);
                return true;
            } catch (RuntimeException ignored) {
                // Current Work Session authority is decisive; project visibility alone is insufficient.
            }
        }
        return false;
    }

    private Map<String, Object> workflowDecisionView(WorkSessionRunSnapshot run) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("runId", run.runId());
        item.put("projectId", run.projectId());
        item.put("sessionId", run.sessionId());
        item.put("owner", run.owner());
        item.put("agentId", run.agentId());
        item.put("status", run.status().name());
        item.put("goal", AnalysisTaskPresentationPolicy.publicGoal(
                run.requestPayload(), "Workflow requires human decision"));
        item.put("updatedAt", run.updatedAt().toString());
        return Map.copyOf(item);
    }

    public List<Map<String, Object>> myAudits(String username, String userId, int limit) {
        LinkedHashMap<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (String operator : principalKeys(username, userId)) {
            for (Map<String, Object> item : configAuditService.listForOperator(operator, limit)) {
                String id = String.valueOf(item.getOrDefault("id", rows.size()));
                rows.putIfAbsent(id, auditSummary(item));
            }
        }
        return rows.values().stream()
                .limit(Math.max(1, Math.min(limit, 200)))
                .toList();
    }

    private List<Map<String, Object>> projectViews(
            List<ProjectCatalogEntry> projects,
            String username,
            String userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectCatalogEntry project : projects) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", project.projectId());
            item.put("name", project.name());
            item.put("description", project.description());
            item.put("owner", project.owner());
            item.put("defaultAgentId", project.defaultAgentId());
            item.put("enabled", project.enabled());
            try {
                item.put("projectRole", projectAccessUseCase.role(
                        project.projectId(), username, userId, false).name());
            } catch (RuntimeException ignored) {
                item.put("projectRole", "NONE");
            }
            result.add(item);
        }
        return List.copyOf(result);
    }

    private List<ProjectCatalogEntry> authorizedProjects(String username, String userId) {
        return projectAccessUseCase.catalog(username, userId, false);
    }

    private Map<String, Object> auditSummary(Map<String, Object> item) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", item.get("id"));
        summary.put("projectId", item.getOrDefault("project_id", ""));
        summary.put("moduleName", item.getOrDefault("module_name", ""));
        summary.put("actionName", item.getOrDefault("action_name", ""));
        summary.put("targetId", item.getOrDefault("target_id", ""));
        summary.put("operatorName", item.getOrDefault("operator_name", ""));
        summary.put("operatorRole", item.getOrDefault("operator_role", ""));
        summary.put("createdAt", item.getOrDefault("create_time", ""));
        summary.put("summary", item.getOrDefault("module_name", "") + " / " + item.getOrDefault("action_name", ""));
        return summary;
    }

    private List<String> principalKeys(String username, String userId) {
        List<String> keys = new ArrayList<>();
        if (StringUtils.hasText(username)) {
            keys.add(username.trim());
        }
        if (StringUtils.hasText(userId) && keys.stream().noneMatch(item -> same(item, userId))) {
            keys.add(userId.trim());
        }
        return keys;
    }

    private String safeUserId(String username, String userId) {
        return StringUtils.hasText(userId) ? userId.trim() : text(username);
    }

    private boolean same(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int listSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof String text && StringUtils.hasText(text) && text.trim().startsWith("[")) {
            try {
                return com.alibaba.fastjson.JSON.parseArray(text).size();
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
