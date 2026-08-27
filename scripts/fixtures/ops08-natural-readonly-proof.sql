-- Set @acceptance_run_id to the actual browser Run ID before executing.
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT('section','run','runId',run_id,'status',status,'leaseReleased',lease_expires_at IS NULL)
FROM ai_ops_agent_run WHERE run_id=@acceptance_run_id;
SELECT JSON_OBJECT('section','calls','total',COUNT(*),'succeeded',SUM(status='SUCCEEDED'),'writes',SUM(read_only=0))
FROM ai_ops_mcp_tool_call WHERE run_id=@acceptance_run_id;
SELECT JSON_OBJECT('section','orders','environment','prod','receipts',COUNT(*),'matched',SUM(r.event_id IS NOT NULL),
 'sameStatus',SUM(j.http_status=r.http_status),'sameVersion',SUM(j.version=r.version),'errors',SUM(r.http_status>=400))
FROM ai_ops_mcp_tool_call c JOIN JSON_TABLE(c.output_json, '$.normalizedContent.requests[*]'
 COLUMNS(trace_id VARCHAR(36) PATH '$.traceId', http_status INT PATH '$.httpStatus', version VARCHAR(40) PATH '$.version')) j
LEFT JOIN ops_acceptance_business_a.ops04_request r ON r.event_id=j.trace_id
WHERE c.run_id=@acceptance_run_id AND c.tool_name='prod_check_orders';
SELECT JSON_OBJECT('section','orders','environment','test','receipts',COUNT(*),'matched',SUM(r.event_id IS NOT NULL),
 'sameStatus',SUM(j.http_status=r.http_status),'sameVersion',SUM(j.version=r.version),'errors',SUM(r.http_status>=400))
FROM ai_ops_mcp_tool_call c JOIN JSON_TABLE(c.output_json, '$.normalizedContent.requests[*]'
 COLUMNS(trace_id VARCHAR(36) PATH '$.traceId', http_status INT PATH '$.httpStatus', version VARCHAR(40) PATH '$.version')) j
LEFT JOIN ops_acceptance_business_prepare.ops04_request r ON r.event_id=j.trace_id
WHERE c.run_id=@acceptance_run_id AND c.tool_name='test_check_orders';
SELECT JSON_OBJECT('section','resource','serviceId',service_id,'environment','prod','version',version,'scenario',scenario)
FROM ops_acceptance_business_a.acceptance_service;
SELECT JSON_OBJECT('section','resource','serviceId',service_id,'environment','test','version',version,'scenario',scenario)
FROM ops_acceptance_business_prepare.acceptance_service;
COMMIT;
