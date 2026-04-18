package cn.lgs.orbisops.trigger.ops.change;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Spring AI input protocol for the PrepareChangePackage tool. */
public final class OpsChangePackageToolInput {

    @com.fasterxml.jackson.annotation.JsonPropertyDescription("实际服务目录中的服务 ID，不得猜造")
    private String serviceId;
    @com.fasterxml.jackson.annotation.JsonPropertyDescription("审批前冻结的验收标准。需要业务 Workflow C 时提供一个 OBSERVABILITY_SLO_V1 对象。serviceId/environment/expectedVersion/baselineVersion/resourceIdentity/routeDefinition/collectionDefinition/changeKind 必须是非空字符串，采集定义和路由直接引用真实回执中的字符串，不能用解释对象或列表替代；maxErrorRate/maxP95Seconds/minQps/maxQps 必须是有限数值。资源和采集定义来自真实证据，阈值来自约定，不得编造或放宽。")
    private List<Map<String, Object>> verificationCriteria = new ArrayList<>();
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

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public List<Map<String, Object>> getVerificationCriteria() { return verificationCriteria; }
    public void setVerificationCriteria(List<Map<String, Object>> verificationCriteria) { this.verificationCriteria = verificationCriteria; }
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
