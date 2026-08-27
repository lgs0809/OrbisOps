-- Local acceptance inventory only. Never changes approvals, services, or run state.
-- Each result is one JSON object. Counts include previous tests, not just this batch.
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('section','clock','databaseNow',CURRENT_TIMESTAMP(3));
SELECT JSON_OBJECT('section','service','environment','prod','serviceId',service_id,
 'version',version,'scenario',scenario) FROM ops_acceptance_business_a.acceptance_service;
SELECT JSON_OBJECT('section','service','environment','test','serviceId',service_id,
 'version',version,'scenario',scenario) FROM ops_acceptance_business_prepare.acceptance_service;
SELECT JSON_OBJECT('section','business','environment','prod',
 'customers',(SELECT COUNT(*) FROM ops_acceptance_business_a.acceptance_customer),
 'orders',(SELECT COUNT(*) FROM ops_acceptance_business_a.acceptance_order),
 'requests',(SELECT COUNT(*) FROM ops_acceptance_business_a.ops04_request),
 'deploymentReceipts',(SELECT COUNT(*) FROM ops_acceptance_business_a.ops08_deployment_receipt));
SELECT JSON_OBJECT('section','business','environment','test',
 'customers',(SELECT COUNT(*) FROM ops_acceptance_business_prepare.acceptance_customer),
 'orders',(SELECT COUNT(*) FROM ops_acceptance_business_prepare.acceptance_order),
 'requests',(SELECT COUNT(*) FROM ops_acceptance_business_prepare.ops04_request),
 'deploymentReceipts',(SELECT COUNT(*) FROM ops_acceptance_business_prepare.ops08_deployment_receipt));
SELECT JSON_OBJECT('section','package','packageId',package_id,'status',status,
 'version',version,'approvedVersion',approved_version,'targetEnvironment',target_environment)
 FROM ai_ops_change_package WHERE project_id='ops-acceptance-a' ORDER BY id DESC LIMIT 20;
SELECT JSON_OBJECT('section','run','runId',run_id,'status',status,
 'cancelRequested',cancel_requested,'leaseExpiresAt',lease_expires_at)
 FROM ai_ops_agent_run WHERE project_id='ops-acceptance-a' ORDER BY id DESC LIMIT 30;
SELECT JSON_OBJECT('section','mcpCall','runId',run_id,'callId',call_id,
 'tool',tool_name,'status',status,'readOnly',read_only)
 FROM ai_ops_mcp_tool_call WHERE project_id='ops-acceptance-a' ORDER BY id DESC LIMIT 30;
COMMIT;
