import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

export interface TaskScheduleRequestDTO {
  id?: number;
  projectId?: string;
  executionType?: 'DEFAULT_REACT' | 'WORKFLOW';
  agentId?: string;
  agentBindingMode?: 'LATEST_PUBLISHED' | 'PINNED_VERSION';
  agentVersion?: number;
  taskName: string;
  description?: string;
  cronExpression: string;
  taskParam?: string;
  status?: number;
  rangeMinutes?: number;
  promWindow?: string;
  includeRecentLogs?: boolean;
  maxRounds?: number;
  subAgentMaxIterations?: number;
  nodeTimeoutSeconds?: number;
  maxEvidenceItems?: number;
  notifyChannel?: boolean;
  notificationChannelId?: string;
  notificationTarget?: string;
  lightweightScreeningEnabled?: boolean;
  screeningSourceType?: 'PROMETHEUS';
  screeningPrimaryUri?: string;
  maxErrorRatePercent?: number;
  maxCpuPercent?: number;
  maxHeapPercent?: number;
  minInstanceUpRatio?: number;
}

export interface TaskScheduleResponseDTO {
  id: number;
  projectId?: string;
  executionType?: 'DEFAULT_REACT' | 'WORKFLOW';
  agentId: string;
  agentBindingMode?: 'LATEST_PUBLISHED' | 'PINNED_VERSION';
  agentVersion?: number;
  agentDefinitionHash?: string;
  taskName: string;
  description?: string;
  cronExpression: string;
  taskParam?: string;
  status: number;
  rangeMinutes?: number;
  promWindow?: string;
  includeRecentLogs?: boolean;
  maxRounds?: number;
  subAgentMaxIterations?: number;
  nodeTimeoutSeconds?: number;
  maxEvidenceItems?: number;
  notifyChannel?: boolean;
  notificationChannelId?: string;
  notificationTarget?: string;
  lightweightScreeningEnabled?: boolean;
  screeningSourceType?: 'PROMETHEUS';
  screeningPrimaryUri?: string;
  maxErrorRatePercent?: number;
  maxCpuPercent?: number;
  maxHeapPercent?: number;
  minInstanceUpRatio?: number;
  createTime?: string;
  updateTime?: string;
}

export interface TaskExecutionResponseDTO {
  id: number;
  scheduleId: number;
  taskName: string;
  agentId: string;
  triggerType: string;
  status: string;
  startedAt?: string;
  endedAt?: string;
  input?: string;
  output?: string;
  errorMessage?: string;
}

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

class TaskScheduleAdminService {
  private baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/task-schedule`;

  async list(projectId: string): Promise<ApiResponse<TaskScheduleResponseDTO[]>> {
    return this.request(`/list?projectId=${encodeURIComponent(projectId)}`);
  }

  async create(request: TaskScheduleRequestDTO): Promise<ApiResponse<boolean>> {
    return this.request('/create', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async update(request: TaskScheduleRequestDTO): Promise<ApiResponse<boolean>> {
    return this.request('/update-by-id', {
      method: 'PUT',
      body: JSON.stringify(request),
    });
  }

  async updateStatus(projectId: string, id: number, status: number): Promise<ApiResponse<boolean>> {
    return this.request(`/status/${id}?status=${status}&projectId=${encodeURIComponent(projectId)}`, {
      method: 'PUT',
    });
  }

  async delete(projectId: string, id: number): Promise<ApiResponse<boolean>> {
    return this.request(`/delete-by-id/${id}?projectId=${encodeURIComponent(projectId)}`, {
      method: 'DELETE',
    });
  }

  async runNow(projectId: string, id: number): Promise<ApiResponse<number>> {
    return this.request(`/run-now/${id}?projectId=${encodeURIComponent(projectId)}`, {
      method: 'POST',
    });
  }

  async listExecutions(projectId: string, scheduleId: number, limit = 20): Promise<ApiResponse<TaskExecutionResponseDTO[]>> {
    const query = `?projectId=${encodeURIComponent(projectId)}&scheduleId=${scheduleId}&limit=${limit}`;
    return this.request(`/execution/list${query}`);
  }

  private async request<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: init?.method || 'GET',
      headers: {
        ...DEFAULT_HEADERS,
        ...(init?.headers || {}),
      },
      body: init?.body,
    });

    const body = await response.json().catch(() => null) as ApiResponse<T> | null;
    if (!response.ok) {
      throw new Error(body?.info || `请求失败（HTTP ${response.status}）`);
    }
    if (!body) {
      throw new Error('服务返回了无法识别的响应');
    }
    return body;
  }
}

export const taskScheduleAdminService = new TaskScheduleAdminService();
