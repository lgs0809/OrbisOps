package cn.lgs.orbisops.trigger.ops.change;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Spring AI input protocol for the PrepareChangePackage tool. */
public final class OpsChangePackageToolInput {

    private String incidentId;
    private String environment;
    private String title;
    private String summary;
    private String diagnosis;
    private String packageType;
    private String riskLevel;
    private List<Map<String, Object>> actions = new ArrayList<>();
    private List<Map<String, Object>> candidatePlans = new ArrayList<>();
    private Map<String, Object> preflightResult = new LinkedHashMap<>();
    private Map<String, Object> dryRunResult = new LinkedHashMap<>();
    private Map<String, Object> approvalBoundary = new LinkedHashMap<>();
    private Map<String, Object> preferredPlan = new LinkedHashMap<>();
    private Map<String, Object> adjustmentPolicy = new LinkedHashMap<>();

    public OpsChangePackageToolInput() {
    }

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public List<Map<String, Object>> getActions() { return actions; }
    public void setActions(List<Map<String, Object>> actions) { this.actions = actions; }
    public List<Map<String, Object>> getCandidatePlans() { return candidatePlans; }
    public void setCandidatePlans(List<Map<String, Object>> candidatePlans) { this.candidatePlans = candidatePlans; }
    public Map<String, Object> getPreflightResult() { return preflightResult; }
    public void setPreflightResult(Map<String, Object> preflightResult) { this.preflightResult = preflightResult; }
    public Map<String, Object> getDryRunResult() { return dryRunResult; }
    public void setDryRunResult(Map<String, Object> dryRunResult) { this.dryRunResult = dryRunResult; }
    public Map<String, Object> getApprovalBoundary() { return approvalBoundary; }
    public void setApprovalBoundary(Map<String, Object> approvalBoundary) { this.approvalBoundary = approvalBoundary; }
    public Map<String, Object> getPreferredPlan() { return preferredPlan; }
    public void setPreferredPlan(Map<String, Object> preferredPlan) { this.preferredPlan = preferredPlan; }
    public Map<String, Object> getAdjustmentPolicy() { return adjustmentPolicy; }
    public void setAdjustmentPolicy(Map<String, Object> adjustmentPolicy) { this.adjustmentPolicy = adjustmentPolicy; }
}
