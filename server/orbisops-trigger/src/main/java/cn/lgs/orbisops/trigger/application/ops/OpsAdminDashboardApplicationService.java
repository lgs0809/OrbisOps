package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.changepackage.service.ChangePackageStatusPolicy;
import cn.lgs.orbisops.domain.analysis.service.AnalysisTaskPresentationPolicy;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import cn.lgs.orbisops.trigger.ops.OpsResourceHealthService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpsAdminDashboardApplicationService {

    private static final ChangePackageStatusPolicy CHANGE_PACKAGE_STATUS = new ChangePackageStatusPolicy();

    private final ProjectDefinitionApplicationService projectDefinitionService;
    private final IncidentQueryApplicationService incidentQueries;
    private final ChangePackageQueryService changePackageQueries;
    private final WorkSessionRunApplicationService workSessionRuns;
    private final OpsResourceHealthService resourceHealthService;

    public OpsAdminDashboardApplicationService(ProjectDefinitionApplicationService projectDefinitionService,
                                               IncidentQueryApplicationService incidentQueries,
                                               ChangePackageQueryService changePackageQueries,
                                               WorkSessionRunApplicationService workSessionRuns,
                                               OpsResourceHealthService resourceHealthService) {
        this.projectDefinitionService = projectDefinitionService;
        this.incidentQueries = incidentQueries;
        this.changePackageQueries = changePackageQueries;
        this.workSessionRuns = workSessionRuns;
        this.resourceHealthService = resourceHealthService;
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> projects = projectDefinitionService.listEnabled();
        List<String> enabledProjectIds = projects.stream()
                .map(project -> String.valueOf(project.getOrDefault("projectId", "")).trim())
                .filter(projectId -> !projectId.isBlank())
                .distinct()
                .toList();
        List<Map<String, Object>> changes = changePackageQueries.list(new ChangePackageListQuery(Map.of(), 100));
        List<IncidentSnapshot> incidents = enabledProjectIds.stream()
                .flatMap(projectId -> incidentQueries.list(projectId, null, 200).stream())
                .sorted(Comparator.comparing(
                        (IncidentSnapshot incident) -> incident.updateTime() == null ? "" : incident.updateTime())
                        .reversed())
                .limit(200)
                .toList();
        List<WorkSessionRunSnapshot> pendingWorkflowDecisions = workSessionRuns
                .listByStatus(WorkSessionRunStatus.WAITING_APPROVAL, 200).stream()
                .filter(run -> enabledProjectIds.contains(run.projectId()))
                .limit(100)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentIncidentCount", incidents.stream()
                .filter(item -> item.status() != IncidentStatus.RESOLVED && item.status() != IncidentStatus.CLOSED)
                .count());
        result.put("actionRequiredIncidentCount", incidents.stream()
                .filter(item -> item.status() == IncidentStatus.ACTION_REQUIRED)
                .count());
        result.put("unownedActionRequiredIncidentCount", incidents.stream()
                .filter(item -> item.status() == IncidentStatus.ACTION_REQUIRED)
                .filter(item -> item.ownerUserId() == null || item.ownerUserId().isBlank())
                .count());
        result.put("investigatingIncidentCount", incidents.stream()
                .filter(item -> item.status() == IncidentStatus.INVESTIGATING)
                .count());
        result.put("verifyingIncidentCount", incidents.stream()
                .filter(item -> item.status() == IncidentStatus.VERIFYING)
                .count());
        result.put("recentIncidents", incidents.stream().limit(12).toList());
        result.put("pendingChangeCount", changes.stream()
                .filter(plan -> CHANGE_PACKAGE_STATUS.pendingReview(String.valueOf(plan.getOrDefault("status", ""))))
                .count());
        result.put("failedChangeCount", changes.stream()
                .filter(plan -> CHANGE_PACKAGE_STATUS.failed(String.valueOf(plan.getOrDefault("status", ""))))
                .count());
        result.put("runningChangeCount", changes.stream()
                .filter(plan -> CHANGE_PACKAGE_STATUS.running(String.valueOf(plan.getOrDefault("status", ""))))
                .count());
        result.put("pendingWorkflowDecisionCount", pendingWorkflowDecisions.size());
        result.put("pendingWorkflowDecisions", pendingWorkflowDecisions.stream()
                .map(this::workflowDecisionView)
                .toList());
        result.put("recentChanges", changes.stream().limit(12).toList());
        result.put("capabilityHealth", resourceHealthService.snapshot());
        result.put("projects", projects);
        return result;
    }

    private Map<String, Object> workflowDecisionView(WorkSessionRunSnapshot run) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.runId());
        result.put("projectId", run.projectId());
        result.put("sessionId", run.sessionId());
        result.put("owner", run.owner());
        result.put("agentId", run.agentId());
        result.put("status", run.status().name());
        result.put("goal", AnalysisTaskPresentationPolicy.publicGoal(
                run.requestPayload(), "Workflow requires human decision"));
        result.put("updatedAt", run.updatedAt().toString());
        return Map.copyOf(result);
    }

}
