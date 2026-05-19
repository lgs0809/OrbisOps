import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

export interface UserProjectSummary {
  projectId: string;
  name?: string;
  description?: string;
  defaultAgentId?: string;
  projectRole?: string;
  readyForInvestigation?: boolean;
  [key: string]: unknown;
}

export interface UserAgentSummary {
  agentId: string;
  name?: string;
  projectId?: string;
  projectName?: string;
  lifecycle?: string;
  description?: string;
  [key: string]: unknown;
}

export interface UserChatSessionSummary {
  sessionId: string;
  userId?: string;
  projectId?: string;
  agentId?: string;
  title?: string;
  status?: string;
  messageCount?: number;
  lastMessage?: string;
  lastActiveAt?: string;
  createdAt?: string;
  [key: string]: unknown;
}

export interface UserExecutionSummary {
  packageId: string;
  projectId?: string;
  environment?: string;
  title?: string;
  summary?: string;
  requester?: string;
  proposalSource?: string;
  sourceRunId?: string;
  diagnosis?: string;
  status?: string;
  riskLevel?: string;
  packageHash?: string;
  approvedVersion?: number;
  approvedPackageHash?: string;
  version?: number;
  requiredApprovals?: number;
  approvalCount?: number;
  actionCount?: number;
  approvalExpiresAt?: string;
  createdAt?: string;
  updatedAt?: string;
  relation?: string;
  canApprove?: boolean;
  canReject?: boolean;
  canVerify?: boolean;
}

export interface UserAuditSummary {
  id: number | string;
  projectId?: string;
  moduleName?: string;
  actionName?: string;
  targetId?: string;
  operatorName?: string;
  operatorRole?: string;
  createdAt?: string;
  summary?: string;
}

export interface UserIncidentSummary {
  incidentId: string;
  projectId?: string;
  title?: string;
  status?: string;
  severity?: string;
  serviceName?: string;
  ownerUserId?: string;
  updateTime?: string;
  [key: string]: unknown;
}

export interface UserWorkflowDecisionSummary {
  runId: string;
  projectId: string;
  sessionId?: string;
  owner?: string;
  agentId?: string;
  status: string;
  goal?: string;
  updatedAt?: string;
}

export interface UserDashboardOverview {
  currentIncidents: UserIncidentSummary[];
  actionRequiredIncidents: UserIncidentSummary[];
  availableProjects: UserProjectSummary[];
  recentSessions: UserChatSessionSummary[];
  pendingExecutions: UserExecutionSummary[];
  pendingApprovals: UserExecutionSummary[];
  pendingWorkflowDecisions: UserWorkflowDecisionSummary[];
  pendingOperations: UserExecutionSummary[];
  workQueue?: {
    incidentActionCount: number;
    approvalCount: number;
    workflowDecisionCount?: number;
    operationCount: number;
    auditReminderCount: number;
  };
  auditReminders: UserAuditSummary[];
}

interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

class OpsUserService {
  private baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/user`;

  dashboardOverview(): Promise<ApiResponse<UserDashboardOverview>> {
    return this.request('/dashboard/overview');
  }

  myExecutions(status?: string, limit = 100): Promise<ApiResponse<UserExecutionSummary[]>> {
    const params = new URLSearchParams({ limit: String(limit) });
    if (status) params.set('status', status);
    return this.request(`/my-executions?${params.toString()}`);
  }

  myAudits(limit = 100): Promise<ApiResponse<UserAuditSummary[]>> {
    const params = new URLSearchParams({ limit: String(limit) });
    return this.request(`/my-audits?${params.toString()}`);
  }

  private async request<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'GET',
      ...init,
      headers: {
        ...DEFAULT_HEADERS,
        ...(init?.headers || {}),
      },
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const payload = await response.json();
    if (payload?.code && payload.code !== '0000') {
      throw new Error(payload.info || '请求失败');
    }
    return payload;
  }
}

export const opsUserService = new OpsUserService();
