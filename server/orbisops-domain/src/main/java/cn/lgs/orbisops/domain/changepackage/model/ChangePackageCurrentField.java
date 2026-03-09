package cn.lgs.orbisops.domain.changepackage.model;

public enum ChangePackageCurrentField {
    OBJECTIVE("objective", "", false),
    SUMMARY("summary", "", false),
    EVIDENCE_JSON("evidenceJson", "{}", false),
    RISK_LEVEL("riskLevel", null, false),
    TOOL_BINDINGS_JSON("toolBindingsJson", "[]", false),
    PREFLIGHT_RESULT_JSON("preflightResultJson", "{}", false),
    DRY_RUN_RESULT_JSON("dryRunResultJson", "{}", false),
    VALIDATION_ASSESSMENT("validationAssessment", "", false),
    REASON_CODE("reasonCode", "", false),
    APPROVAL_BOUNDARY_JSON("approvalBoundaryJson", "{}", false),
    PREFERRED_PLAN_JSON("preferredPlanJson", "{}", false),
    ADJUSTMENT_POLICY_JSON("adjustmentPolicyJson", "{}", false),
    TARGET_ENVIRONMENT("targetEnvironment", "", false),
    TARGET_SCOPE_JSON("targetScopeJson", "{}", false),
    ALLOWED_TOOLS_JSON("allowedToolsJson", "[]", false),
    FORBIDDEN_TOOLS_JSON("forbiddenToolsJson", "[]", false),
    BRANCH_NAME("branchName", "", true),
    BASE_BRANCH("baseBranch", "", true),
    TARGET_BRANCH("targetBranch", "", true),
    BASE_COMMIT("baseCommit", "", true),
    REPAIR_WORKSPACE_ID("repairWorkspaceId", "", true),
    REPOSITORY_ID("repositoryId", "", true),
    SERVICE_ID("serviceId", "", true),
    REPAIR_COMMIT("repairCommit", "", true),
    DIFF_SUMMARY("diffSummary", "", true),
    DIFF_HASH("diffHash", "", true),
    TEST_COMMAND("testCommand", "", true),
    TEST_PROOF_HASH("testProofHash", "", true),
    ARTIFACT_DIGEST("artifactDigest", "", true),
    CHANGED_FILES_JSON("changedFilesJson", "[]", false),
    CODE_EVIDENCE_JSON("codeEvidenceJson", "{}", false),
    BASH_EVIDENCE_JSON("bashEvidenceJson", "{}", false),
    LSP_EVIDENCE_JSON("lspEvidenceJson", "{}", false),
    MCP_STEPS_JSON("mcpStepsJson", "[]", false),
    ROLLBACK_STEPS_JSON("rollbackStepsJson", "[]", false),
    VERIFICATION_CRITERIA_JSON("verificationCriteriaJson", "[]", false),
    CI_RESULT_JSON("ciResultJson", "{}", false),
    LANDING_PLAN_JSON("landingPlanJson", "{}", false),
    ALLOWED_LANDING_ADJUSTMENTS_JSON("allowedLandingAdjustmentsJson", "[]", false),
    LANDING_RESULT_JSON("landingResultJson", "{}", false),
    FAILURE_SUMMARY_JSON("failureSummaryJson", "{}", false),
    CLEANUP_PLAN_JSON("cleanupPlanJson", "{}", false);

    private final String snapshotKey;
    private final String defaultValue;
    private final boolean nullable;

    ChangePackageCurrentField(String snapshotKey, String defaultValue, boolean nullable) {
        this.snapshotKey = snapshotKey;
        this.defaultValue = defaultValue;
        this.nullable = nullable;
    }

    public String snapshotKey() {
        return snapshotKey;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public boolean nullable() {
        return nullable;
    }
}
