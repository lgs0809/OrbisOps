export interface McpTemplateFormState {
  templateId: string;
  templateName: string;
  resourceType: string;
  transportType: string;
  supportedActionsText: string;
  riskLevel: string;
  readOnly: string;
  description: string;
  status: string;
}

export const emptyMcpTemplateForm: McpTemplateFormState = {
  templateId: '',
  templateName: '',
  resourceType: 'mysql',
  transportType: 'stdio',
  supportedActionsText: 'SHOW_SCHEMA,SELECT,EXPLAIN',
  riskLevel: 'LOW',
  readOnly: 'true',
  description: '',
  status: 'ENABLED',
};

export const mcpResourcePresets: Record<string, {
  label: string;
  actions: string[];
  readOnly: string;
  riskLevel: string;
}> = {
  mysql: { label: 'MySQL', actions: ['SHOW_SCHEMA', 'SELECT', 'EXPLAIN'], readOnly: 'true', riskLevel: 'LOW' },
  postgresql: { label: 'PostgreSQL', actions: ['SHOW_SCHEMA', 'SELECT', 'EXPLAIN'], readOnly: 'true', riskLevel: 'LOW' },
  redis: { label: 'Redis', actions: ['TYPE', 'TTL', 'GET', 'SCAN_NAMESPACE'], readOnly: 'true', riskLevel: 'LOW' },
  elasticsearch: { label: 'Elasticsearch / OpenSearch', actions: ['SEARCH_LOGS', 'AGGREGATE', 'VIEW_MAPPING'], readOnly: 'true', riskLevel: 'LOW' },
  prometheus: { label: 'Prometheus', actions: ['QUERY_RANGE', 'QUERY_INSTANT', 'TARGET_HEALTH'], readOnly: 'true', riskLevel: 'LOW' },
  grafana: { label: 'Grafana', actions: ['READ_DASHBOARD', 'READ_DATASOURCE', 'QUERY_PANEL'], readOnly: 'true', riskLevel: 'LOW' },
  rabbitmq: { label: 'RabbitMQ', actions: ['LIST_QUEUES', 'INSPECT_QUEUE', 'READ_POLICY'], readOnly: 'true', riskLevel: 'MEDIUM' },
  kubernetes: { label: 'Kubernetes', actions: ['GET_RESOURCE', 'DESCRIBE', 'LOGS', 'DRY_RUN_APPLY'], readOnly: 'true', riskLevel: 'MEDIUM' },
  nacos: { label: 'Nacos', actions: ['READ_CONFIG', 'READ_HISTORY', 'VALIDATE_CONFIG'], readOnly: 'true', riskLevel: 'MEDIUM' },
  jenkins: { label: 'Jenkins', actions: ['READ_JOB', 'READ_BUILD_LOG', 'DRY_RUN_PIPELINE'], readOnly: 'true', riskLevel: 'MEDIUM' },
  gitlab_ci: { label: 'GitLab CI', actions: ['READ_PIPELINE', 'READ_JOB_LOG', 'TRIGGER_DRY_RUN'], readOnly: 'true', riskLevel: 'MEDIUM' },
  cmdb: { label: 'CMDB', actions: ['READ_SERVICE', 'READ_OWNER', 'READ_DEPENDENCY'], readOnly: 'true', riskLevel: 'LOW' },
  http_api: { label: 'HTTP API / Bridge', actions: ['GET', 'HEAD', 'VALIDATE_REQUEST', 'DRY_RUN'], readOnly: 'true', riskLevel: 'MEDIUM' },
  webhook: { label: 'Webhook', actions: ['VALIDATE_PAYLOAD', 'DRY_RUN_NOTIFY'], readOnly: 'false', riskLevel: 'MEDIUM' },
  custom: { label: '自定义 Bridge', actions: ['READ_STATUS', 'VALIDATE_ONLY', 'DRY_RUN'], readOnly: 'true', riskLevel: 'MEDIUM' },
};

export const mcpActionOptions = Array.from(new Set(Object.values(mcpResourcePresets).flatMap((preset) => preset.actions))).sort();
