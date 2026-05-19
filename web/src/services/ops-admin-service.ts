import { API_CONFIG } from '../config/api';
import { readProjectContextId } from '../utils/project-context';
import type { OpsProjectWorkspace } from './ops-project-service';
import { ApiResponse, assertOpsResponse as assertOk, buildOpsHeaders as buildHeaders, opsRequest } from './ops-http-client';
import type { OpsChangePackage } from './ops-change-package-types';

export { ApiRequestError } from './ops-http-client';
export type { ApiResponse } from './ops-http-client';
export type { OpsChangePackage, OpsChangePackageCapabilities, OpsChangePackageEvent, OpsLandingOperationRun } from './ops-change-package-types';

export interface OpsAgentRunRequestDTO {
  runId?: string;
  query?: string;
  question?: string;
  rangeMinutes?: number;
  promWindow?: string;
  includeRecentLogs?: boolean;
  projectId?: string;
  scopeType?: 'PLATFORM_TEMPLATE' | 'PROJECT' | string;
  agentDefinitionId?: string;
  agentVersion?: number;
  agentDefinitionSnapshotJson?: string;
  maxRounds?: number;
  subAgentMaxIterations?: number;
  nodeTimeoutSeconds?: number;
  maxEvidenceItems?: number;
  notifyChannel?: boolean;
  notificationChannelId?: string;
  notificationTarget?: string;
  triggerSource?: string;
  triggerEventId?: string;
}

export interface OpsDataSourceStatusDTO {
  name: string;
  url: string;
  available: boolean | null;
  message: string;
}

export interface OpsInvestigationTaskDTO {
  source: string;
  agent: string;
  goal: string;
  reason: string;
  priority: number;
  condition?: string;
}

export interface OpsInvestigationPlanDTO {
  intent: string;
  reason: string;
  tasks: OpsInvestigationTaskDTO[];
  conditionalTasks: OpsInvestigationTaskDTO[];
  skippedTasks: OpsInvestigationTaskDTO[];
}

export interface OpsInvestigationAttemptDTO {
  query: string;
  resultCount: number;
  reason: string;
}

export interface OpsInvestigationResultDTO {
  source: string;
  agent: string;
  status: 'FOUND' | 'NOT_FOUND' | 'INSUFFICIENT' | 'BLOCKED' | 'ERROR' | string;
  summary: string;
  evidence: string[];
  attempts: OpsInvestigationAttemptDTO[];
  gaps: string[];
  suggestedAdjustments: string[];
  shouldRetry: boolean;
  confidence: number;
}

export interface OpsAnalysisResponseDTO {
  analysisId: string;
  agentDefinitionId?: string;
  agentVersion?: number;
  agentRuntime?: string;
  rangeMinutes: number;
  promWindow: string;
  generatedAt: string;
  elasticsearchStatus: OpsDataSourceStatusDTO;
  prometheusStatus: OpsDataSourceStatusDTO;
  mysqlSlowSqlStatus?: OpsDataSourceStatusDTO;
  logSummary: {
    totalLogs: number;
    errorLogs: number;
    warnLogs: number;
    levelCounts?: Record<string, number>;
    topLoggers?: Array<{ key: string; count: number }>;
  };
  metricSummary: {
    instanceTotal: number;
    instanceUp: number;
    totalQps: number;
    errorQps?: number;
    errorRate: number;
    heapMemoryUsagePercent: number;
    processCpuUsagePercent: number;
  };
  slowSqlSummary?: {
    totalStatements: number;
    slowStatements: number;
    avgQueryTimeMs: number;
    maxQueryTimeMs: number;
    rowsExamined: number;
  };
  endpointMetrics?: Array<{
    uri: string;
    method: string;
    status: string;
    qps: number;
    avgResponseMs: number;
  }>;
  recentLogs?: Array<{
    timestamp: string;
    level: string;
    loggerName: string;
    message: string;
  }>;
  slowSqlSamples?: Array<{
    startTime?: string;
    databaseName?: string;
    userHost?: string;
    digest?: string;
    sqlText?: string;
    queryTimeMs?: number;
    rowsExamined?: number;
    rowsSent?: number;
    countStar?: number;
  }>;
  insights?: Array<{
    level: string;
    title: string;
    detail: string;
    suggestion: string;
  }>;
  investigationPlan?: OpsInvestigationPlanDTO;
  investigationResults?: OpsInvestigationResultDTO[];
  agentExecutionSteps?: OpsAgentExecutionStepDTO[];
  executionNotes?: string[];
  aiPrompt: string;
  markdownReport: string;
  structuredReport?: Record<string, any>;
}

export interface OpsResourceHealthCheck {
  id: string;
  name: string;
  endpoint?: string;
  healthy: boolean | null;
  message?: string;
  [key: string]: any;
}

export interface OpsConfigAuditRecord {
  id: number;
  audit_id?: string;
  project_id?: string;
  agent_id?: string;
  module_name?: string;
  action_name?: string;
  target_type?: string;
  target_id?: string;
  risk_level?: string;
  result_status?: string;
  operator_id?: string;
  operator_name?: string;
  operator_role?: string;
  client_ip?: string;
  trace_id?: string;
  before_json?: string;
  after_json?: string;
  create_time?: string;
  [key: string]: any;
}

export interface OpsConfigAuditQuery {
  projectId?: string;
  userId?: string;
  agentId?: string;
  module?: string;
  action?: string;
  riskLevel?: string;
  startTime?: string;
  endTime?: string;
  limit?: number;
}

export interface OpsAuditPolicy {
  projectId?: string;
  project_id?: string;
  retentionDays?: number;
  retention_days?: number;
  maskingEnabled?: boolean | number;
  masking_enabled?: boolean | number;
  exportApprovalRequired?: boolean | number;
  export_approval_required?: boolean | number;
  highRiskConfirmationRequired?: boolean | number;
  high_risk_confirmation_required?: boolean | number;
  replayEnabled?: boolean | number;
  replay_enabled?: boolean | number;
  status?: string;
  stateVersion?: number;
  updateTime?: string;
  update_time?: string;
  persistence?: boolean;
  [key: string]: any;
}

export interface RagEvalCase {
  id?: number;
  caseName?: string;
  query: string;
  knowledgeTag?: string;
  expectedKeywords?: string[];
  topK?: number;
  enabled?: boolean;
}

export interface OpsAgentExecutionStepDTO {
  nodeId: string;
  nodeType: string;
  agent: string;
  source: string;
  status: string;
  summary: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
}

export interface OpsAuditRecordDTO {
  analysisId: string;
  success: boolean;
  question?: string;
  intent?: string;
  rangeMinutes?: number;
  promWindow?: string;
  generatedAt: string;
  durationMs?: number;
  selectedSources?: string[];
  executedSources?: string[];
  skippedSources?: string[];
  resultStatuses?: Record<string, string>;
  insightLevels?: string[];
  conclusion?: string;
  errorMessage?: string;
}

export interface OpsAgentRunRecordDTO {
  runId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | string;
  request?: OpsAgentRunRequestDTO;
  response?: OpsAnalysisResponseDTO;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
  durationMs?: number;
}

export interface OpsAlertTriggerRule {
  id?: number;
  ruleName: string;
  status?: number;
  sourceType?: string;
  alertNameRegex?: string;
  severityRegex?: string;
  serviceRegex?: string;
  matchLabelsJson?: string;
  notificationChannelId?: string;
  notificationTarget?: string;
  webhookSecret?: string;
  projectId?: string;
  agentDefinitionId?: string;
  agentBindingMode?: 'LATEST_PUBLISHED' | 'PINNED_VERSION';
  agentVersion?: number;
  agentDefinitionHash?: string;
  questionTemplate?: string;
  rangeMinutes?: number;
  promWindow?: string;
  includeRecentLogs?: boolean;
  notifyChannel?: boolean;
  subAgentMaxIterations?: number;
  nodeTimeoutSeconds?: number;
  maxEvidenceItems?: number;
  dedupWindowSeconds?: number;
  createTime?: string;
  updateTime?: string;
}

export interface OpsAlertTriggerEvent {
  id: number;
  ruleId?: number;
  ruleName?: string;
  sourceType: string;
  status: string;
  dedupKey?: string;
  fingerprint: string;
  alertName?: string;
  severity?: string;
  serviceName?: string;
  receiver?: string;
  runId?: string;
  runStatus?: string;
  finalSummary?: string;
  completedAt?: string;
  errorMessage?: string;
  labelsJson?: string;
  annotationsJson?: string;
  payloadJson?: string;
  createTime?: string;
}

export interface OpsIncident {
  id?: number;
  incidentId: string;
  projectId?: string;
  title: string;
  status: string;
  severity?: string;
  serviceName?: string;
  sourceType?: string;
  fingerprint?: string;
  dedupKey?: string;
  currentRunId?: string;
  ownerUserId?: string;
  summary?: string;
  labelsJson?: string;
  metadataJson?: string;
  occurrenceCount?: number;
  affectedResourcesJson?: string;
  firstSeenAt?: string;
  lastSeenAt?: string;
  createTime?: string;
  updateTime?: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
  reviewedAt?: string;
}

export interface OpsIncidentTimelineItem {
  id?: number;
  incidentId: string;
  eventType: string;
  title: string;
  detail?: string;
  actor?: string;
  refType?: string;
  refId?: string;
  payloadJson?: string;
  createTime?: string;
}

export interface OpsDiagnosisEvidenceRef {
  evidenceRef: string;
  resultId: string;
  outputHash: string;
}

export interface OpsDiagnosisFact {
  factId: string;
  statement: string;
  evidenceRefs: OpsDiagnosisEvidenceRef[];
}

export interface OpsDiagnosisInference {
  statement: string;
  supports: string[];
}

export interface OpsDiagnosisSourceStatus {
  sourceId: string;
  sourceName?: string;
  queryStatus: 'NOT_CONFIGURED' | 'NOT_QUERIED' | 'SUCCEEDED' | 'FAILED' | 'INSUFFICIENT';
  assessment: 'NORMAL' | 'ABNORMAL' | 'UNKNOWN';
  state?: 'NOT_CONFIGURED' | 'UNAVAILABLE' | 'NOT_QUERIED' | 'UNKNOWN' | 'NORMAL' | 'ABNORMAL';
  detail?: string;
}

export interface OpsDiagnosisResult {
  summary: string;
  impact: string[];
  facts: OpsDiagnosisFact[];
  inferences: OpsDiagnosisInference[];
  excludedHypotheses: string[];
  unknowns: string[];
  recommendations: string[];
  sourceStatus: OpsDiagnosisSourceStatus[];
  evidenceCompleteness: 'COMPLETE' | 'PARTIAL' | 'INSUFFICIENT';
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  requiresAction: boolean;
  suggestedNextAction?: string;
}

export interface OpsIncidentSourceRef {
  sourceType: string;
  sourceId: string;
  title?: string;
  status?: string;
  createTime?: string;
}

export type OpsIncidentChangeRef = Pick<
  OpsChangePackage,
  'packageId' | 'status' | 'version' | 'packageHash' | 'updateTime'
> & Partial<Pick<
  OpsChangePackage,
  'projectId' | 'incidentId' | 'summary' | 'objective' | 'riskLevel' | 'targetEnvironment' | 'reasonCode'
>> & {
  landingRunId?: string;
};

export interface OpsIncidentWatcher {
  incidentId: string;
  userId: string;
  createdBy?: string;
  createTime?: string;
}

export interface OpsRelatedIncidentRef {
  incidentId: string;
  title?: string;
  status?: string;
  severity?: string;
  relationType?: string;
  createTime?: string;
}

export interface OpsIncidentDetail {
  incident: OpsIncident;
  sourceRefs: OpsIncidentSourceRef[];
  timeline: OpsIncidentTimelineItem[];
  runs: Record<string, any>[];
  changePackages: OpsIncidentChangeRef[];
  watchers: OpsIncidentWatcher[];
  relatedIncidents: OpsRelatedIncidentRef[];
  currentUserWatching?: boolean;
  diagnosis: OpsDiagnosisResult;
  suggestedUserAction?: string;
}

export interface OpsAlertCorrelationGroup {
  groupId: string;
  environment: string;
  revision: number;
  rootCauseConfirmed: boolean;
  anchor: { incidentId: string; title: string; entityId: string };
  members: Array<{
    signal: { incidentId: string; title: string; entityId: string; startedAt?: string; recovery: boolean };
    decision: { reason: string; score: number; evidenceRefs: string[]; policyVersion: string };
    occurrenceCount: number;
  }>;
}

export interface OpsAlertWebhookResult {
  receivedAlerts: number;
  matchedRules: number;
  triggeredRuns: number;
  dedupedAlerts: number;
  events: OpsAlertTriggerEvent[];
}

export interface OpsGraphEvent {
  runId?: string;
  analysisId?: string;
  sequence: number;
  eventType: string;
  nodeId?: string;
  nodeType?: string;
  agent?: string;
  source?: string;
  status?: string;
  summary?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  payload?: Record<string, any>;
}

export interface OpsMcpServerConfig {
  name?: string;
  description?: string;
  transport?: string;
  command?: string;
  url?: string;
  timeoutSeconds?: number;
  args?: string[];
  env?: Record<string, string>;
  headers?: Record<string, string>;
  toolCapabilities?: Record<string, 'read_only' | 'notification' | 'mutating' | 'blocked' | string>;
  allowedTools?: string[];
  notificationTools?: string[];
  blockedTools?: string[];
}

export interface OpsWorkflowNode {
  nodeId: string;
  type: string;
  mode?: 'direct' | 'llm' | 'react' | 'review' | 'auto' | 'plan' | string; // review/auto/plan are legacy read compatibility; editor writes direct/llm/react only.
  agent?: string;
  description?: string;
  instruction?: string;
  modelId?: string;
  subEngine?: string;
  outputKey?: string;
  ragEnabled?: boolean;
  knowledgeBaseId?: string;
  repairEnabled?: boolean;
  changePackageEnabled?: boolean;
  skills?: string[];
  mcpIds?: string[];
  executionTargetIds?: string[];
  mcpServers?: OpsMcpServerConfig[];
  config?: Record<string, any>;
}

export interface OpsGraphEdge {
  edgeId?: string;
  name?: string;
  from: string;
  to: string;
  conditionType?: 'always' | 'route_match' | 'review_decision' | 'expression' | 'contains' | 'error' | 'default' | string;
  condition?: string;
  description?: string;
  priority?: number;
  defaultEdge?: boolean;
  feedback?: boolean;
  dataMapping?: Record<string, any>;
}

export interface OpsLoopPolicy {
  loopId: string;
  name?: string;
  nodes?: string[];
  feedbackEdges?: string[];
  maxRounds?: number;
  stopCondition?: string;
  timeoutSeconds?: number;
  exitEdge?: string;
  countMode?: string;
  config?: Record<string, any>;
}

export interface OpsAgentDefinition {
  agentId: string;
  schemaVersion?: number;
  version?: number;
  definitionHash?: string;
  lifecycle?: string;
  name?: string;
  projectId?: string;
  engine?: string;
  description?: string;
  instruction?: string;
  definitionKind?: 'MAIN_ASSISTANT' | 'SPECIALIZED_WORKFLOW' | string;
  workflowInvocationMode?: 'MANUAL_ONLY' | 'AUTO_ELIGIBLE' | string;
  workflowAutoSelectEnabled?: boolean;
  workflowPriority?: number;
  whenToUse?: string[];
  whenNotToUse?: string[];
  routingKeywords?: string[];
  modelId?: string;
  startNodeId?: string;
  defaultMaxMainRounds?: number;
  defaultSubAgentMaxIterations?: number;
  ragEnabled?: boolean;
  knowledgeBaseId?: string;
  queryRewriteEnabled?: boolean;
  changePackageEnabled?: boolean;
  skills?: string[];
  mcpIds?: string[];
  executionTargetIds?: string[];
  nodes?: OpsWorkflowNode[];
  edges?: OpsGraphEdge[];
  loops?: OpsLoopPolicy[];
  mcpServers?: OpsMcpServerConfig[];
}

export interface OpsAgentCapabilitySet {
  projectId: string;
  projectName?: string;
  skillIds?: string[];
  mcpIds?: string[];
  knowledgeBaseIds?: string[];
  projectSkills?: Array<Record<string, any>>;
  enabledGlobalSkills?: Array<Record<string, any>>;
  projectTools?: Array<Record<string, any>>;
  enabledSharedTools?: Array<Record<string, any>>;
  projectKnowledgeBases?: Array<Record<string, any>>;
  enabledGlobalKnowledgeBases?: Array<Record<string, any>>;
  executionTargets?: Array<Record<string, any>>;
  rawProjectFields?: Record<string, any>;
}

export interface OpsAgentCapabilityBinding {
  id?: number;
  agentId?: string;
  version?: number;
  lifecycle?: string;
  projectId?: string;
  ownerType?: 'AGENT' | 'NODE' | 'AGENTSCOPE' | string;
  nodeId?: string;
  capabilityType: 'skill' | 'project_tool' | 'knowledge_base' | 'inline_mcp_server' | 'execution_target' | string;
  capabilityId: string;
  capabilityScope?: string;
  bindConfig?: Record<string, any>;
  bindConfigJson?: string;
  createTime?: string;
}

export interface OpsAgentBindingValidationResult {
  valid: boolean;
  projectId?: string;
  errors?: string[];
  warnings?: string[];
  skillRefs?: string[];
  mcpRefs?: string[];
  knowledgeBaseRefs?: string[];
  executionTargetRefs?: string[];
}

export interface OpsRuntimeEvent {
  eventType?: string;
  runId?: string;
  nodeId?: string;
  nodeType?: string;
  agent?: string;
  source?: string;
  status?: string;
  summary?: string;
  content?: string;
  timestamp?: string;
  sessionId?: string;
  payload?: Record<string, any>;
}

export interface OpsSkillReference {
  type: 'agent' | 'node' | string;
  agentId?: string;
  agentName?: string;
  targetId?: string;
  projectId?: string;
}

export interface OpsSkillSummary {
  name: string;
  catalogManaged?: boolean;
  builtIn?: boolean;
  skillId?: string;
  skillName?: string;
  scope?: 'GLOBAL' | 'PROJECT' | string;
  projectId?: string;
  sourceGlobalSkillId?: string;
  version?: number;
  status?: string;
  origin?: 'MANUAL' | 'EVOLVED' | 'IMPORTED' | string;
  updateMode?: 'AUTO' | 'MANUAL_ONLY' | 'FROZEN' | string;
  update_mode?: string;
  autoUpdateEnabled?: boolean | number;
  auto_update_enabled?: boolean | number;
  autoMergeEnabled?: boolean | number;
  auto_merge_enabled?: boolean | number;
  lastEvolvedAt?: string;
  last_evolved_at?: string;
  sourceType?: string;
  description?: string;
  basePath?: string;
  frontMatter?: Record<string, any>;
  contentLength?: number;
  referencedBy?: OpsSkillReference[];
  artifactCount?: number;
  packageSize?: number;
  entrypoint?: string;
  artifacts?: OpsSkillArtifact[];
}

export interface OpsSkillArtifact {
  path: string;
  role: 'ENTRYPOINT' | 'RESOURCE' | 'SCRIPT' | 'TEMPLATE' | 'EVAL' | 'REFERENCE' | 'ASSET' | 'METADATA' | string;
  mediaType?: string;
  encoding?: 'UTF8' | 'BASE64' | string;
  contentHash?: string;
  sizeBytes?: number;
  content?: string;
}

export interface OpsSkillDetail extends OpsSkillSummary {
  content?: string;
  markdown?: string;
  xml?: string;
  artifacts?: OpsSkillArtifact[];
}

export interface OpsSkillContextResponse {
  names: string[];
  content: string;
  length: number;
}

export interface OpsAgentChatRequest {
  userId?: string;
  sessionId?: string;
  query: string;
  mode?: 'SIMPLE' | 'MULTI_TURN' | 'AGENT' | string;
  engine?: string;
  agentDefinitionId?: string;
  agentVersion?: number;
  previewDraft?: boolean;
  projectId?: string;
  agentDefinition?: OpsAgentDefinition;
  modelId?: string;
  ragEnabled?: boolean;
  knowledgeBaseId?: string;
  enableThinking?: boolean;
  metadata?: Record<string, any>;
}

export interface OpsSelectableModel {
  modelId: string;
  modelName?: string;
  modelType?: string;
  modelUsage?: string;
  description?: string;
}

export interface OpsAgentChatResponse {
  sessionId?: string;
  userId?: string;
  agentId?: string;
  agentVersion?: number;
  mode?: string;
  engine?: string;
  content?: string;
  events?: OpsRuntimeEvent[];
  metadata?: Record<string, any>;
}

export interface OpsChatSession {
  sessionId: string;
  userId?: string;
  projectId?: string;
  agentId?: string;
  agentVersion?: number;
  title?: string;
  mode?: string;
  engine?: string;
  ragEnabled?: boolean;
  knowledgeBaseId?: string;
  status?: string;
  stateVersion?: number;
  messageCount?: number;
  lastMessage?: string;
  createdAt?: string;
  lastActiveAt?: string;
  metadata?: Record<string, any>;
}

export interface OpsChatMessage {
  messageId: string;
  sessionId: string;
  userId?: string;
  role: 'user' | 'assistant' | 'system' | string;
  content: string;
  createdAt?: string;
  metadata?: Record<string, any>;
}

export interface OpsChatSessionCreateRequest {
  userId?: string;
  projectId?: string;
  agentId?: string;
  agentVersion?: number;
  title?: string;
  mode?: string;
  engine?: string;
  ragEnabled?: boolean;
  knowledgeBaseId?: string;
  metadata?: Record<string, any>;
}

export interface OpsAnalysisTaskSummary {
  runId: string;
  projectId: string;
  sessionId?: string;
  userId?: string;
  agentId?: string;
  agentVersion?: number;
  agentDefinitionHash?: string;
  executionHarness?: string;
  status: string;
  source: 'CHAT' | 'CHANNEL' | 'ALERTMANAGER' | 'SCHEDULE' | string;
  taskType: 'CHAT' | 'CHANNEL' | 'ALERT' | 'SCHEDULE' | string;
  goal?: string;
  response?: Record<string, any>;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OpsAnalysisTaskDetail extends OpsAnalysisTaskSummary {
  technicalError?: string;
  summary?: string;
  evidenceSufficient?: boolean;
  hypotheses?: string[];
  excludedFindings?: string[];
  unknowns?: string[];
  recommendations?: string[];
  events?: Array<Record<string, any>>;
  evidence?: Array<Record<string, any>>;
  toolResults?: Array<Record<string, any>>;
  changePackages?: OpsChangePackage[];
  incidents?: Array<Record<string, any>>;
  skillUsages?: Array<Record<string, any>>;
  feedback?: Array<Record<string, any>>;
}

export interface OpsWorkflowDecisionSummary {
  runId: string;
  projectId: string;
  sessionId?: string;
  owner?: string;
  agentId?: string;
  status: string;
  goal?: string;
  updatedAt?: string;
}

export interface OpsAdminDashboardOverview {
  currentIncidentCount: number;
  actionRequiredIncidentCount: number;
  unownedActionRequiredIncidentCount: number;
  investigatingIncidentCount: number;
  verifyingIncidentCount: number;
  recentIncidents: OpsIncident[];
  pendingChangeCount: number;
  failedChangeCount: number;
  runningChangeCount: number;
  pendingWorkflowDecisionCount: number;
  pendingWorkflowDecisions: OpsWorkflowDecisionSummary[];
  recentChanges: OpsChangePackage[];
  capabilityHealth: Record<string, any>;
  projects: Array<Record<string, any>>;
}

export class OpsAdminService {
  private baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops`;
  private dashboardBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/dashboard`;
  private runBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops-agent-runs`;
  private agentBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops-agents`;
  private adminChatBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops-agent-chat`;
  private userChatBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/user/chat`;
  private adminAnalysisTaskBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/analysis-tasks`;
  private userAnalysisTaskBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/user/ops/analysis-tasks`;
  private userIncidentBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/user/ops/incidents`;

  private async request<T>(url: string, init?: RequestInit): Promise<ApiResponse<T>> {
    return await opsRequest<T>(url, init);
  }

  async adminDashboardOverview(): Promise<ApiResponse<OpsAdminDashboardOverview>> {
    const response = await fetch(`${this.dashboardBaseUrl}/overview`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    await assertOk(response);
    return await response.json();
  }

  async listAudits(limit = 20): Promise<ApiResponse<OpsAuditRecordDTO[]>> {
    const response = await fetch(`${this.baseUrl}/audits?limit=${encodeURIComponent(limit)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async submitRun(
    request: OpsAgentRunRequestDTO
  ): Promise<ApiResponse<OpsAgentRunRecordDTO>> {
    const response = await fetch(this.runBaseUrl, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async getRun(runId: string): Promise<ApiResponse<OpsAgentRunRecordDTO>> {
    const response = await fetch(`${this.runBaseUrl}/${encodeURIComponent(runId)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listRuns(limit = 20): Promise<ApiResponse<OpsAgentRunRecordDTO[]>> {
    const response = await fetch(`${this.runBaseUrl}?limit=${encodeURIComponent(limit)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async cancelRun(runId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.runBaseUrl}/${encodeURIComponent(runId)}/cancel`, {
      method: 'POST',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  private incidentBaseUrl(scope: 'admin' | 'user' = 'admin'): string {
    return scope === 'user' ? this.userIncidentBaseUrl : `${this.baseUrl}/incidents`;
  }

  async listIncidents(query: {
    projectId: string;
    status?: string;
    limit?: number;
    scope?: 'admin' | 'user';
  }): Promise<ApiResponse<OpsIncident[]>> {
    const projectId = query.projectId.trim();
    if (!projectId) {
      throw new Error('INCIDENT_PROJECT_ID_REQUIRED');
    }
    const params = new URLSearchParams();
    if (query.status) params.set('status', query.status);
    params.set('projectId', projectId);
    params.set('limit', String(query.limit ?? 50));
    const response = await fetch(`${this.incidentBaseUrl(query.scope ?? 'admin')}?${params.toString()}`, {
      method: 'GET',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async getIncidentDetail(
    incidentId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsIncidentDetail>> {
    const response = await fetch(`${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/detail`, {
      method: 'GET',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async listAlertCorrelations(projectId: string, scope: 'admin' | 'user'): Promise<ApiResponse<OpsAlertCorrelationGroup[]>> {
    const params = new URLSearchParams({ projectId, limit: '100' });
    const base = this.incidentBaseUrl(scope).replace(/\/incidents$/, '');
    const response = await fetch(`${base}/alert-correlations?${params}`, { headers: buildHeaders() });
    await assertOk(response);
    return await response.json();
  }

  async createIncident(
    request: Partial<OpsIncident> & { actor?: string },
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsIncident>> {
    const response = await fetch(this.incidentBaseUrl(scope), {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });
    await assertOk(response);
    return await response.json();
  }

  async updateIncidentStatus(
    incidentId: string,
    status: string,
    actor = 'admin',
    note = '',
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsIncident>> {
    const params = new URLSearchParams({ status, actor });
    if (note) params.set('note', note);
    const response = await fetch(`${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/status?${params.toString()}`, {
      method: 'PUT',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async assignIncidentOwner(
    incidentId: string,
    ownerUserId = '',
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsIncident>> {
    const params = new URLSearchParams();
    if (scope === 'admin' && ownerUserId) params.set('ownerUserId', ownerUserId);
    const query = params.toString();
    const response = await fetch(`${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/owner${query ? `?${query}` : ''}`, {
      method: 'PUT',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async addIncidentComment(
    incidentId: string,
    comment: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsIncidentTimelineItem>> {
    return await this.request<OpsIncidentTimelineItem>(
      `${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/comments`,
      { method: 'POST', body: JSON.stringify({ comment }) },
    );
  }

  async addIncidentWatcher(
    incidentId: string,
    userId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<boolean>> {
    const suffix = scope === 'user' ? 'me' : encodeURIComponent(userId);
    return await this.request<boolean>(
      `${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/watchers/${suffix}`,
      { method: 'PUT' },
    );
  }

  async removeIncidentWatcher(
    incidentId: string,
    userId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<boolean>> {
    const suffix = scope === 'user' ? 'me' : encodeURIComponent(userId);
    return await this.request<boolean>(
      `${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/watchers/${suffix}`,
      { method: 'DELETE' },
    );
  }

  async relateIncident(
    incidentId: string,
    relatedIncidentId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<boolean>> {
    return await this.request<boolean>(
      `${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/relations/${encodeURIComponent(relatedIncidentId)}`,
      { method: 'PUT' },
    );
  }

  async unrelateIncident(
    incidentId: string,
    relatedIncidentId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<boolean>> {
    return await this.request<boolean>(
      `${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/relations/${encodeURIComponent(relatedIncidentId)}`,
      { method: 'DELETE' },
    );
  }

  async listIncidentTimeline(
    incidentId: string,
    limit = 100,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsIncidentTimelineItem[]>> {
    const response = await fetch(`${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/timeline?limit=${encodeURIComponent(limit)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async appendIncidentTimeline(
    incidentId: string,
    request: Partial<OpsIncidentTimelineItem>,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsIncidentTimelineItem>> {
    const response = await fetch(`${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/timeline`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });
    await assertOk(response);
    return await response.json();
  }

  async confirmIncidentHelpful(
    incidentId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsIncidentTimelineItem>> {
    return await this.request<OpsIncidentTimelineItem>(
      `${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/feedback/helpful`,
      { method: 'POST' },
    );
  }

  async verifyIncident(
    incidentId: string,
    packageId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<{ status: 'PASSED' | 'FAILED' | 'INSUFFICIENT'; summary: string; evidence: Record<string, any> }>> {
    const query = new URLSearchParams({ packageId });
    return await this.request(
      `${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/verification?${query.toString()}`,
      { method: 'POST' },
    );
  }

  async linkIncidentRun(
    incidentId: string,
    runId: string,
    detail = 'AI 对话关联运维分析 run',
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<boolean>> {
    const params = new URLSearchParams();
    if (detail) params.set('detail', detail);
    const response = await fetch(`${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/runs/${encodeURIComponent(runId)}?${params.toString()}`, {
      method: 'POST',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async listIncidentRuns(
    incidentId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<Record<string, any>[]>> {
    const response = await fetch(`${this.incidentBaseUrl(scope)}/${encodeURIComponent(incidentId)}/runs`, {
      method: 'GET',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async listAlertTriggerRules(): Promise<ApiResponse<OpsAlertTriggerRule[]>> {
    const response = await fetch(`${this.baseUrl}/alert-triggers/rules`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async saveAlertTriggerRule(rule: OpsAlertTriggerRule): Promise<ApiResponse<OpsAlertTriggerRule>> {
    const response = await fetch(`${this.baseUrl}/alert-triggers/rules`, {
      method: rule.id ? 'PUT' : 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(rule),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateAlertTriggerRuleStatus(id: number, status: number): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/alert-triggers/rules/${encodeURIComponent(id)}/status?status=${encodeURIComponent(status)}`, {
      method: 'PUT',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteAlertTriggerRule(id: number): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/alert-triggers/rules/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listAlertTriggerEvents(limit = 50): Promise<ApiResponse<OpsAlertTriggerEvent[]>> {
    const response = await fetch(`${this.baseUrl}/alert-triggers/events?limit=${encodeURIComponent(limit)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listRunEvents(runId: string): Promise<ApiResponse<OpsGraphEvent[]>> {
    const response = await fetch(`${this.runBaseUrl}/${encodeURIComponent(runId)}/events/list`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async streamRunEvents(
    runId: string,
    onEvent: (event: OpsGraphEvent) => void,
    signal?: AbortSignal
  ): Promise<void> {
    const response = await fetch(`${this.runBaseUrl}/${encodeURIComponent(runId)}/events`, {
      method: 'GET',
      headers: {
        ...buildHeaders(),
        Accept: 'text/event-stream',
      },
      signal,
    });

    if (!response.ok || !response.body) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    await readSseStream(response, (event) => onEvent(event as OpsGraphEvent));
  }

  async createChatSession(request: OpsChatSessionCreateRequest): Promise<ApiResponse<string>> {
    const response = await fetch(`${this.userChatBaseUrl}/session`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listChatSessions(params: {
    userId?: string;
    agentId?: string;
    keyword?: string;
    favorite?: boolean;
    limit?: number;
  } = {}): Promise<ApiResponse<OpsChatSession[]>> {
    const query = new URLSearchParams();
    if (params.userId) query.set('userId', params.userId);
    if (params.agentId) query.set('agentId', params.agentId);
    if (params.keyword) query.set('keyword', params.keyword);
    if (params.favorite !== undefined) query.set('favorite', String(params.favorite));
    query.set('limit', String(params.limit || 50));
    const response = await fetch(`${this.userChatBaseUrl}/sessions?${query.toString()}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listChatMessages(sessionId: string, limit = 200): Promise<ApiResponse<OpsChatMessage[]>> {
    const response = await fetch(`${this.userChatBaseUrl}/sessions/${encodeURIComponent(sessionId)}/messages?limit=${encodeURIComponent(limit)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listChatEvents(sessionId: string): Promise<ApiResponse<OpsGraphEvent[]>> {
    const response = await fetch(`${this.userChatBaseUrl}/sessions/${encodeURIComponent(sessionId)}/events`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async sendChatMessage(sessionId: string, request: OpsAgentChatRequest): Promise<ApiResponse<OpsAgentChatResponse>> {
    const response = await fetch(`${this.userChatBaseUrl}/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateChatSession(sessionId: string, request: { title?: string; status?: string; metadata?: Record<string, any>; expectedStateVersion: number }): Promise<ApiResponse<OpsChatSession>> {
    const response = await fetch(`${this.userChatBaseUrl}/sessions/${encodeURIComponent(sessionId)}`, {
      method: 'PUT',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteChatSession(sessionId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.userChatBaseUrl}/sessions/${encodeURIComponent(sessionId)}`, {
      method: 'DELETE',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async chatModelStatus(): Promise<ApiResponse<Record<string, any>>> {
    const response = await fetch(`${this.userChatBaseUrl}/model-status`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listAnalysisTasks(params: {
    projectId: string;
    source?: string;
    status?: string;
    limit?: number;
    scope?: 'admin' | 'user';
  }): Promise<ApiResponse<OpsAnalysisTaskSummary[]>> {
    const query = new URLSearchParams({
      projectId: params.projectId,
      limit: String(params.limit || 100),
    });
    if (params.source) query.set('source', params.source);
    if (params.status) query.set('status', params.status);
    const baseUrl = params.scope === 'user' ? this.userAnalysisTaskBaseUrl : this.adminAnalysisTaskBaseUrl;
    return await this.request<OpsAnalysisTaskSummary[]>(`${baseUrl}?${query.toString()}`, { method: 'GET' });
  }

  async getAnalysisTask(
    projectId: string,
    runId: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<OpsAnalysisTaskDetail>> {
    const baseUrl = scope === 'user' ? this.userAnalysisTaskBaseUrl : this.adminAnalysisTaskBaseUrl;
    return await this.request<OpsAnalysisTaskDetail>(
      `${baseUrl}/${encodeURIComponent(runId)}?projectId=${encodeURIComponent(projectId)}`,
      { method: 'GET' },
    );
  }

  async recordAnalysisTaskFeedback(
    projectId: string,
    runId: string,
    feedbackType: 'HELPFUL' | 'INACCURATE' | 'INSUFFICIENT_EVIDENCE',
    comment: string,
    scope: 'admin' | 'user' = 'admin',
  ): Promise<ApiResponse<Record<string, any>>> {
    const baseUrl = scope === 'user' ? this.userAnalysisTaskBaseUrl : this.adminAnalysisTaskBaseUrl;
    return await this.request<Record<string, any>>(
      `${baseUrl}/${encodeURIComponent(runId)}/feedback?projectId=${encodeURIComponent(projectId)}`,
      {
        method: 'POST',
        body: JSON.stringify({ feedbackType, comment }),
      },
    );
  }

  async streamUserChat(
    request: OpsAgentChatRequest,
    onEvent: (event: OpsRuntimeEvent) => void,
    signal?: AbortSignal
  ): Promise<void> {
    const response = await fetch(`${this.userChatBaseUrl}/stream`, {
      method: 'POST',
      headers: {
        ...buildHeaders(),
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(request),
      signal,
    });

    if (!response.ok || !response.body) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    await readSseStream(response, (event) => onEvent(event as OpsRuntimeEvent));
  }

  async getUserChatRun(runId: string, projectId: string, signal?: AbortSignal): Promise<ApiResponse<{
    status: string; error_message?: string; response?: { content?: string };
  }>> {
    const params = new URLSearchParams({ projectId });
    const response = await fetch(`${this.userChatBaseUrl}/runs/${encodeURIComponent(runId)}?${params}`, {
      method: 'GET', headers: buildHeaders(), signal,
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return await response.json();
  }

  async cancelUserChatRun(runId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.userChatBaseUrl}/runs/${encodeURIComponent(runId)}/cancel`, {
      method: 'POST',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async telemetry(): Promise<ApiResponse<Record<string, any>>> {
    const response = await fetch(`${this.baseUrl}/telemetry`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async productMetrics(): Promise<ApiResponse<Record<string, any>>> {
    const response = await fetch(`${this.baseUrl}/product-metrics`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async resourceHealth(): Promise<ApiResponse<{ generatedAt?: string; checks: OpsResourceHealthCheck[]; healthyCount?: number; degradedCount?: number }>> {
    const response = await fetch(`${this.baseUrl}/resources/health`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async resourceCapabilities(): Promise<ApiResponse<Record<string, any>[]>> {
    const response = await fetch(`${this.baseUrl}/resources/capabilities`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listConfigAudits(query: OpsConfigAuditQuery): Promise<ApiResponse<OpsConfigAuditRecord[]>> {
    const params = new URLSearchParams();
    if (query.projectId) params.set('projectId', query.projectId);
    if (query.userId) params.set('userId', query.userId);
    if (query.agentId) params.set('agentId', query.agentId);
    if (query.module) params.set('module', query.module);
    if (query.action) params.set('action', query.action);
    if (query.riskLevel) params.set('riskLevel', query.riskLevel);
    if (query.startTime) params.set('startTime', query.startTime);
    if (query.endTime) params.set('endTime', query.endTime);
    params.set('limit', String(query.limit || 100));
    const response = await fetch(`${this.baseUrl}/config-audits?${params.toString()}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async getConfigAudit(auditId: string): Promise<ApiResponse<OpsConfigAuditRecord>> {
    const response = await fetch(`${this.baseUrl}/config-audits/${encodeURIComponent(auditId)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async exportConfigAudit(auditId: string): Promise<ApiResponse<Record<string, any>>> {
    const response = await fetch(`${this.baseUrl}/config-audits/${encodeURIComponent(auditId)}/export`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async getAuditPolicy(projectId?: string): Promise<ApiResponse<OpsAuditPolicy>> {
    const params = new URLSearchParams();
    if (projectId) params.set('projectId', projectId);
    const response = await fetch(`${this.baseUrl}/config-audits/policy?${params.toString()}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateAuditPolicy(request: OpsAuditPolicy): Promise<ApiResponse<OpsAuditPolicy>> {
    const response = await fetch(`${this.baseUrl}/config-audits/policy`, {
      method: 'PUT',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listSkills(): Promise<ApiResponse<OpsSkillSummary[]>> {
    const response = await fetch(`${this.baseUrl}/skills`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listGlobalSkills(): Promise<ApiResponse<OpsSkillSummary[]>> {
    const response = await fetch(`${this.baseUrl}/skills/global`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async createGlobalSkill(payload: {
    skillId: string;
    name?: string;
    description?: string;
    category: string;
    subcategory?: string;
    whenToUse: string[];
    whenNotToUse: string[];
    keywords?: string[];
    content?: string;
    status?: string;
    artifacts?: OpsSkillArtifact[];
    evalSuites?: string[];
  }): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(`${this.baseUrl}/skills/global`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async getSkill(name: string): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(`${this.baseUrl}/skills/${encodeURIComponent(name)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async getGlobalSkill(skillId: string): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(`${this.baseUrl}/skills/global/${encodeURIComponent(skillId)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listGlobalSkillArtifacts(skillId: string): Promise<ApiResponse<OpsSkillArtifact[]>> {
    const response = await fetch(`${this.baseUrl}/skills/global/${encodeURIComponent(skillId)}/artifacts`, {
      method: 'GET',
      headers: buildHeaders(),
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return await response.json();
  }

  async updateSkill(name: string, content: string): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(`${this.baseUrl}/skills/${encodeURIComponent(name)}`, {
      method: 'PUT',
      headers: buildHeaders(),
      body: JSON.stringify({ content }),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateGlobalSkill(skillId: string, payload: { content?: string; markdown?: string; name?: string; description?: string; status?: string; artifacts?: OpsSkillArtifact[]; evalSuites?: string[] }): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(`${this.baseUrl}/skills/global/${encodeURIComponent(skillId)}`, {
      method: 'PUT',
      headers: buildHeaders(),
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateGlobalSkillStatus(skillId: string, status: 'ENABLED' | 'DISABLED' | string): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(`${this.baseUrl}/skills/global/${encodeURIComponent(skillId)}/status`, {
      method: 'PATCH',
      headers: buildHeaders(),
      body: JSON.stringify({ status }),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listGlobalSkillUsage(skillId: string): Promise<ApiResponse<OpsSkillReference[]>> {
    const response = await fetch(`${this.baseUrl}/skills/global/${encodeURIComponent(skillId)}/usage`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listProjectSkills(projectId: string): Promise<ApiResponse<OpsSkillSummary[]>> {
    const response = await fetch(`${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async createProjectSkill(
    projectId: string,
    payload: {
      skillId: string;
      name?: string;
      description?: string;
      content?: string;
      status?: string;
      artifacts?: OpsSkillArtifact[];
      evalSuites?: string[];
    },
  ): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(`${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async getProjectSkill(projectId: string, skillId: string): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(
      `${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/${encodeURIComponent(skillId)}`,
      {
        method: 'GET',
        headers: buildHeaders(),
      },
    );
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async listProjectSkillArtifacts(projectId: string, skillId: string): Promise<ApiResponse<OpsSkillArtifact[]>> {
    const response = await fetch(
      `${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/${encodeURIComponent(skillId)}/artifacts`,
      { method: 'GET', headers: buildHeaders() },
    );
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return await response.json();
  }

  async updateProjectSkill(
    projectId: string,
    skillId: string,
    payload: { name?: string; description?: string; content?: string; status?: string; artifacts?: OpsSkillArtifact[]; evalSuites?: string[] },
  ): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(
      `${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/${encodeURIComponent(skillId)}`,
      {
        method: 'PUT',
        headers: buildHeaders(),
        body: JSON.stringify(payload),
      },
    );
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async updateProjectSkillStatus(
    projectId: string,
    skillId: string,
    status: 'ENABLED' | 'DISABLED' | string,
  ): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(
      `${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/${encodeURIComponent(skillId)}/status`,
      {
        method: 'PATCH',
        headers: buildHeaders(),
        body: JSON.stringify({ status }),
      },
    );
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async listProjectSkillUsage(
    projectId: string,
    skillId: string,
  ): Promise<ApiResponse<OpsSkillReference[]>> {
    const response = await fetch(
      `${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/${encodeURIComponent(skillId)}/usage`,
      {
        method: 'GET',
        headers: buildHeaders(),
      },
    );
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async copyGlobalSkillToProject(projectId: string, payload: { globalSkillId: string; skillId?: string; name?: string; description?: string }): Promise<ApiResponse<OpsSkillDetail>> {
    const response = await fetch(`${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/copy-from-global`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteSkill(name: string): Promise<ApiResponse<OpsSkillSummary[]>> {
    const response = await fetch(`${this.baseUrl}/skills/${encodeURIComponent(name)}`, {
      method: 'DELETE',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async reloadSkills(): Promise<ApiResponse<OpsSkillSummary[]>> {
    const response = await fetch(`${this.baseUrl}/skills/reload`, {
      method: 'POST',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async renderSkillContext(names: string[], maxChars = 12000): Promise<ApiResponse<OpsSkillContextResponse>> {
    const params = new URLSearchParams();
    names.forEach((name) => params.append('names', name));
    params.set('maxChars', String(maxChars));
    const response = await fetch(`${this.baseUrl}/skills/context?${params.toString()}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateGlobalSkillUpdateMode(
    skillId: string,
    payload: { updateMode?: string; autoUpdateEnabled?: boolean; autoMergeEnabled?: boolean },
  ): Promise<ApiResponse<OpsSkillDetail>> {
    return await this.request(`${this.baseUrl}/skills/global/${encodeURIComponent(skillId)}/update-mode`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  }

  async listGlobalSkillVersions(skillId: string): Promise<ApiResponse<Record<string, any>[]>> {
    return await this.request(`${this.baseUrl}/skills/global/${encodeURIComponent(skillId)}/versions`, {
      method: 'GET',
    });
  }

  async rollbackGlobalSkillVersion(skillId: string, version: number): Promise<ApiResponse<OpsSkillDetail>> {
    return await this.request(
      `${this.baseUrl}/skills/global/${encodeURIComponent(skillId)}/versions/${encodeURIComponent(version)}/rollback`,
      { method: 'POST' },
    );
  }

  async updateProjectSkillUpdateMode(
    projectId: string,
    skillId: string,
    payload: { updateMode?: string; autoUpdateEnabled?: boolean; autoMergeEnabled?: boolean },
  ): Promise<ApiResponse<OpsSkillDetail>> {
    return await this.request(
      `${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/${encodeURIComponent(skillId)}/update-mode`,
      {
        method: 'PATCH',
        body: JSON.stringify(payload),
      },
    );
  }

  async listProjectSkillVersions(projectId: string, skillId: string): Promise<ApiResponse<Record<string, any>[]>> {
    return await this.request(
      `${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/${encodeURIComponent(skillId)}/versions`,
      { method: 'GET' },
    );
  }

  async rollbackProjectSkillVersion(
    projectId: string,
    skillId: string,
    version: number,
  ): Promise<ApiResponse<OpsSkillDetail>> {
    return await this.request(
      `${this.baseUrl}/skills/projects/${encodeURIComponent(projectId)}/${encodeURIComponent(skillId)}/versions/${encodeURIComponent(version)}/rollback`,
      { method: 'POST' },
    );
  }

  async listContextMemories(params: {
    scopeType?: string;
    scopeId?: string;
    memoryType?: string;
    status?: string;
    keyword?: string;
    limit?: number;
  } = {}): Promise<ApiResponse<Record<string, any>[]>> {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) search.set(key, String(value));
    });
    return await this.request(`${this.baseUrl}/context-memories?${search.toString()}`, { method: 'GET' });
  }

  async createContextMemory(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/context-memories`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  async updateContextMemory(memoryId: string, payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/context-memories/${encodeURIComponent(memoryId)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  async updateContextMemoryStatus(memoryId: string, status: string): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/context-memories/${encodeURIComponent(memoryId)}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  }

  async extractContextMemoryCandidates(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>[]>> {
    return await this.request(`${this.baseUrl}/context-memories/extract-candidates`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  async getTaskContext(runId: string): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/runs/${encodeURIComponent(runId)}/task-context`, {
      method: 'GET',
    });
  }

  async listSkillEvolverJobs(params: {
    status?: string;
    projectId?: string;
    limit?: number;
  } = {}): Promise<ApiResponse<Record<string, any>[]>> {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) search.set(key, String(value));
    });
    return await this.request(`${this.baseUrl}/skill-evolver/jobs?${search.toString()}`, { method: 'GET' });
  }

  async getSkillEvolverJob(jobId: string): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/skill-evolver/jobs/${encodeURIComponent(jobId)}`, {
      method: 'GET',
    });
  }

  async createSkillEvolverJob(payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/skill-evolver/jobs`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  async listAtomicSkillPublications(projectId: string): Promise<ApiResponse<Record<string, any>[]>> {
    return this.request(`${this.baseUrl}/skill-evolver/atomic-publications?projectId=${encodeURIComponent(projectId)}`, { method: 'GET' });
  }

  async listSkillMaintenance(projectId: string): Promise<ApiResponse<Record<string, any>[]>> {
    return this.request(`${this.baseUrl}/skill-evolver/maintenance?projectId=${encodeURIComponent(projectId)}`, { method: 'GET' });
  }

  async keepInactiveSkill(id: string, projectId: string, reason: string): Promise<ApiResponse<Record<string, any>>> {
    return this.request(`${this.baseUrl}/skill-evolver/maintenance/${encodeURIComponent(id)}/keep`, { method: 'POST', body: JSON.stringify({ projectId, reason }) });
  }

  async skillPublicationStatus(candidateId: string, projectId: string): Promise<ApiResponse<Record<string, any>>> {
    return this.request(`${this.baseUrl}/skill-evolver/candidates/${encodeURIComponent(candidateId)}/publication?projectId=${encodeURIComponent(projectId)}`, { method: 'GET' });
  }

  async retrySkillPublication(candidateId: string, projectId: string): Promise<ApiResponse<Record<string, any>>> {
    return this.request(`${this.baseUrl}/skill-evolver/candidates/${encodeURIComponent(candidateId)}/publication/retry`, { method: 'POST', body: JSON.stringify({ projectId }) });
  }

  async rollbackAtomicSkillPublication(candidateId: string, projectId: string, reason: string): Promise<ApiResponse<Record<string, any>>> {
    return this.request(`${this.baseUrl}/skill-evolver/atomic-publications/${encodeURIComponent(candidateId)}/rollback`, {
      method: 'POST', body: JSON.stringify({ projectId, reason }),
    });
  }

  async runSkillEvolverOnce(limit = 5): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/skill-evolver/jobs/run-once?limit=${encodeURIComponent(limit)}`, {
      method: 'POST',
    });
  }

  async listSkillEvolverPatches(params: {
    jobId?: string;
    projectId?: string;
    targetSkillId?: string;
    limit?: number;
  } = {}): Promise<ApiResponse<Record<string, any>[]>> {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') search.set(key, String(value));
    });
    return await this.request(`${this.baseUrl}/skill-evolver/patches?${search.toString()}`, { method: 'GET' });
  }

  async getRemoteMcpCatalogStatus(projectId: string): Promise<ApiResponse<Record<string, any>[]>> {
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-remote-catalogs`, { method: 'GET' });
  }

  async getToolCatalogSummary(projectId: string): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/tool-catalog-summary`, {
      method: 'GET',
    });
  }

  async rebuildToolCatalogSummary(projectId: string): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/tool-catalog-summary/rebuild`, {
      method: 'POST',
    });
  }

  async selectToolRoute(projectId: string, payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/tool-router/select`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  async hydrateToolSchema(projectId: string, payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/tool-router/hydrate-schema`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  async listMcpToolSnapshots(
    projectId: string,
    params: { limit?: number } = {},
  ): Promise<ApiResponse<Record<string, any>[]>> {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) search.set(key, String(value));
    });
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-tool-snapshots?${search.toString()}`, {
      method: 'GET',
    });
  }

  async listMcpToolPolicies(
    projectId: string,
    params: { limit?: number } = {},
  ): Promise<ApiResponse<Record<string, any>[]>> {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) search.set(key, String(value));
    });
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-tool-policies?${search.toString()}`, {
      method: 'GET',
    });
  }

  async listMcpToolActivations(
    projectId: string,
    params: { limit?: number } = {},
  ): Promise<ApiResponse<Record<string, any>[]>> {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) search.set(key, String(value));
    });
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-tool-activations?${search.toString()}`, {
      method: 'GET',
    });
  }

  async approveMcpToolPolicy(
    projectId: string,
    policyId: string,
    payload: Record<string, any> = {},
  ): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-tool-policies/${encodeURIComponent(policyId)}/approve`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
    );
  }

  async rejectMcpToolPolicy(
    projectId: string,
    policyId: string,
    payload: Record<string, any> = {},
  ): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-tool-policies/${encodeURIComponent(policyId)}/reject`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
    );
  }

  async disableMcpToolPolicy(
    projectId: string,
    policyId: string,
    payload: Record<string, any> = {},
  ): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-tool-policies/${encodeURIComponent(policyId)}/disable`,
      {
        method: 'PATCH',
        body: JSON.stringify(payload),
      },
    );
  }

  async listToolRoutingDecisions(
    projectId: string,
    params: { agentId?: string; limit?: number } = {},
  ): Promise<ApiResponse<Record<string, any>[]>> {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') search.set(key, String(value));
    });
    return await this.request(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/tool-router/decisions?${search.toString()}`,
      { method: 'GET' },
    );
  }

  async listMcpToolCalls(
    projectId: string,
    params: { toolId?: string; status?: string; limit?: number } = {},
  ): Promise<ApiResponse<Record<string, any>[]>> {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') search.set(key, String(value));
    });
    return await this.request(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-tool-calls?${search.toString()}`, {
      method: 'GET',
    });
  }

  async getMcpToolCall(projectId: string, callId: string): Promise<ApiResponse<Record<string, any>>> {
    return await this.request(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/mcp-tool-calls/${encodeURIComponent(callId)}`,
      { method: 'GET' },
    );
  }

  async listAgents(projectId?: string): Promise<ApiResponse<OpsAgentDefinition[]>> {
    const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';
    const response = await fetch(`${this.agentBaseUrl}${query}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async listChatProjects(scope: 'admin' | 'user' = 'user'): Promise<ApiResponse<OpsProjectWorkspace[]>> {
    const catalogBaseUrl = scope === 'admin' ? `${API_CONFIG.BASE_DOMAIN}/api/v1/agent/chat` : this.userChatBaseUrl;
    const response = await fetch(`${catalogBaseUrl}/catalog/projects`, {
      method: 'GET',
      headers: buildHeaders(),
    });
    await assertOk(response);
    const payload = await response.json() as ApiResponse<OpsProjectWorkspace[]>;
    const preferredProjectId = readProjectContextId();
    if (preferredProjectId && Array.isArray(payload.data)) {
      payload.data = [...payload.data].sort((left, right) => {
        if (left.projectId === preferredProjectId) return -1;
        if (right.projectId === preferredProjectId) return 1;
        return 0;
      });
    }
    return payload;
  }

  async listChatAgents(projectId: string): Promise<ApiResponse<OpsAgentDefinition[]>> {
    const response = await fetch(`${this.userChatBaseUrl}/catalog/projects/${encodeURIComponent(projectId)}/agents`, {
      method: 'GET',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async listChatModels(): Promise<ApiResponse<OpsSelectableModel[]>> {
    const response = await fetch(`${this.userChatBaseUrl}/catalog/models`, {
      method: 'GET',
      headers: buildHeaders(),
    });
    await assertOk(response);
    return await response.json();
  }

  async getAgent(agentId: string): Promise<ApiResponse<OpsAgentDefinition>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async getProjectAgentCapabilities(projectId: string): Promise<ApiResponse<OpsAgentCapabilitySet>> {
    const response = await fetch(`${this.agentBaseUrl}/projects/${encodeURIComponent(projectId)}/agent-capabilities`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async validateAgentBindings(agentId: string, definition: OpsAgentDefinition): Promise<ApiResponse<OpsAgentBindingValidationResult>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}/validate-bindings`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(definition),
    });

    await assertOk(response);

    return await response.json();
  }

  async getAgentBindings(agentId: string): Promise<ApiResponse<OpsAgentCapabilityBinding[]>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}/bindings`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async updateAgentBindings(
    agentId: string,
    bindings: OpsAgentCapabilityBinding[],
  ): Promise<ApiResponse<OpsAgentDefinition & { bindings?: OpsAgentCapabilityBinding[] }>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}/bindings`, {
      method: 'PUT',
      headers: buildHeaders(),
      body: JSON.stringify({ bindings }),
    });

    await assertOk(response);

    return await response.json();
  }

  async saveAgent(definition: OpsAgentDefinition): Promise<ApiResponse<OpsAgentDefinition>> {
    const response = await fetch(this.agentBaseUrl, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(definition),
    });

    await assertOk(response);

    return await response.json();
  }

  async saveAgentDraft(definition: OpsAgentDefinition): Promise<ApiResponse<OpsAgentDefinition>> {
    const response = await fetch(`${this.agentBaseUrl}/drafts`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(definition),
    });

    await assertOk(response);

    return await response.json();
  }

  async cloneAgent(sourceAgentId: string, request: { projectId: string; agentId: string; name: string }): Promise<ApiResponse<OpsAgentDefinition>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(sourceAgentId)}/clone`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });

    await assertOk(response);
    return await response.json();
  }

  async deleteAgent(agentId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}`, {
      method: 'DELETE',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async listAgentVersions(agentId: string): Promise<ApiResponse<OpsAgentDefinition[]>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}/versions`, {
      method: 'GET',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async validateAgentVersion(agentId: string, version: number): Promise<ApiResponse<OpsAgentDefinition>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(version)}/validate`, {
      method: 'POST',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async publishAgentVersion(agentId: string, version: number): Promise<ApiResponse<OpsAgentDefinition>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(version)}/publish`, {
      method: 'POST',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async rollbackAgentVersion(agentId: string, version: number): Promise<ApiResponse<OpsAgentDefinition>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(version)}/rollback`, {
      method: 'POST',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async disableAgentVersion(agentId: string, version: number): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.agentBaseUrl}/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(version)}/disable`, {
      method: 'POST',
      headers: buildHeaders(),
    });

    await assertOk(response);

    return await response.json();
  }

  async testRunAgent(request: OpsAgentChatRequest): Promise<ApiResponse<OpsAgentChatResponse>> {
    const response = await fetch(`${this.adminChatBaseUrl}/test-run`, {
      method: 'POST',
      headers: buildHeaders(),
      body: JSON.stringify(request),
    });

    await assertOk(response);

    return await response.json();
  }
}

const readSseStream = async (
  response: Response,
  onEvent: (event: Record<string, any>) => void
): Promise<void> => {
  if (!response.body) {
    return;
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  const flushEvent = (raw: string) => {
    const lines = raw.split(/\r?\n/);
    const dataLines = lines
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart());
    if (dataLines.length === 0) {
      return;
    }
    try {
      onEvent(JSON.parse(dataLines.join('\n')));
    } catch (error) {
      console.warn('SSE 事件解析失败:', error);
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() || '';
    events.forEach(flushEvent);
  }

  if (buffer.trim()) {
    flushEvent(buffer);
  }
};

export const opsAdminService = new OpsAdminService();
