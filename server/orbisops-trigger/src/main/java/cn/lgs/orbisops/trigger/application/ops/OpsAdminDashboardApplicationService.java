package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackageStatusPolicy;
import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
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
    private final ProjectMcpCatalogApplicationService projectMcpCatalogService;
    private final OpsAgentDefinitionApplicationService agentDefinitionApplicationService;
    private final IncidentQueryApplicationService incidentQueries;
    private final ChangePackageQueryService changePackageQueries;
    private final OpsConfigAuditService configAuditService;
    private final OpsResourceHealthService resourceHealthService;

    public OpsAdminDashboardApplicationService(ProjectDefinitionApplicationService projectDefinitionService,
                                               ProjectMcpCatalogApplicationService projectMcpCatalogService,
                                               OpsAgentDefinitionApplicationService agentDefinitionApplicationService,
                                               IncidentQueryApplicationService incidentQueries,
                                               ChangePackageQueryService changePackageQueries,
                                               OpsConfigAuditService configAuditService,
                                               OpsResourceHealthService resourceHealthService) {
        this.projectDefinitionService = projectDefinitionService;
        this.projectMcpCatalogService = projectMcpCatalogService;
        this.agentDefinitionApplicationService = agentDefinitionApplicationService;
        this.incidentQueries = incidentQueries;
        this.changePackageQueries = changePackageQueries;
        this.configAuditService = configAuditService;
        this.resourceHealthService = resourceHealthService;
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> projects = projectDefinitionService.listEnabled();
        List<Map<String, Object>> agents = agentDefinitionApplicationService.listAgents();
        List<Map<String, Object>> changes = changePackageQueries.list(new ChangePackageListQuery(Map.of(), 100));
        List<IncidentSnapshot> incidents = projects.stream()
                .map(project -> String.valueOf(project.getOrDefault("projectId", "")).trim())
                .filter(projectId -> !projectId.isBlank())
                .flatMap(projectId -> incidentQueries.list(projectId, null, 200).stream())
                .sorted(Comparator.comparing(
                        (IncidentSnapshot incident) -> incident.updateTime() == null ? "" : incident.updateTime())
                        .reversed())
                .limit(200)
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
        result.put("recentIncidents", incidents.stream().limit(8).toList());
        result.put("projectCount", projects.size());
        result.put("agentCount", agents.size());
        result.put("pendingChangeCount", changes.stream()
                .filter(plan -> CHANGE_PACKAGE_STATUS.pendingReview(String.valueOf(plan.getOrDefault("status", ""))))
                .count());
        result.put("failedChangeCount", changes.stream()
                .filter(plan -> CHANGE_PACKAGE_STATUS.failed(String.valueOf(plan.getOrDefault("status", ""))))
                .count());
        result.put("runningChangeCount", changes.stream()
                .filter(plan -> CHANGE_PACKAGE_STATUS.running(String.valueOf(plan.getOrDefault("status", ""))))
                .count());
        result.put("highRiskToolCount", highRiskProjectToolCount(projects));
        result.put("auditAlertCount", configAuditService.search(new OpsConfigAuditService.AuditQuery(
                null, null, null, null, null, "HIGH", null, null, 500)).size());
        result.put("recentChanges", changes.stream().limit(10).toList());
        result.put("capabilityHealth", resourceHealthService.snapshot());
        result.put("projects", projects);
        return result;
    }

    private long highRiskProjectToolCount(List<Map<String, Object>> projects) {
        return projects.stream()
                .map(project -> String.valueOf(project.getOrDefault("projectId", "")))
                .filter(projectId -> !projectId.isBlank())
                .flatMap(projectId -> projectMcpCatalogService.list(projectId).stream())
                .filter(tool -> tool.riskLevel() == ProjectMcpRiskLevel.HIGH
                        || tool.riskLevel() == ProjectMcpRiskLevel.CRITICAL)
                .count();
    }
}
