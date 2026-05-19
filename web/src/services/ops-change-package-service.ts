import { API_CONFIG } from '../config/api';
import { ApiResponse, opsRequest } from './ops-http-client';
import {
  OpsChangePackage,
  OpsChangePackageEvent,
  OpsLandingOperationRun,
} from './ops-change-package-types';

export type OpsExecutionScope = 'admin' | 'user';

export interface OpsApprovalChannelOption {
  channelId: string;
  name: string;
  type: 'FEISHU' | 'WECOM' | 'SLACK' | 'DINGTALK';
}

export interface ChangePackageListParams {
  projectId?: string;
  sessionId?: string;
  incidentId?: string;
  status?: string;
  limit?: number;
  scope?: OpsExecutionScope;
}

class OpsChangePackageService {
  private readonly adminBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/change-packages`;
  private readonly userBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/user/ops/change-packages`;

  private baseUrl(scope: OpsExecutionScope = 'admin') {
    return scope === 'user' ? this.userBaseUrl : this.adminBaseUrl;
  }

  async list(params: ChangePackageListParams = {}): Promise<ApiResponse<OpsChangePackage[]>> {
    const query = new URLSearchParams();
    if (params.projectId) query.set('projectId', params.projectId);
    if (params.sessionId) query.set('sessionId', params.sessionId);
    if (params.incidentId && params.scope !== 'user') query.set('incidentId', params.incidentId);
    if (params.status) query.set('status', params.status);
    query.set('limit', String(params.limit || 100));
    return await opsRequest<OpsChangePackage[]>(`${this.baseUrl(params.scope)}?${query.toString()}`, { method: 'GET' });
  }

  async get(packageId: string, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<OpsChangePackage>> {
    return await opsRequest<OpsChangePackage>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}`, { method: 'GET' });
  }

  async listVersions(packageId: string, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<OpsChangePackage[]>> {
    return await opsRequest<OpsChangePackage[]>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/versions`, { method: 'GET' });
  }

  async listEvents(packageId: string, scope: OpsExecutionScope = 'admin', limit = 100): Promise<ApiResponse<OpsChangePackageEvent[]>> {
    return await opsRequest<OpsChangePackageEvent[]>(
      `${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/events?limit=${encodeURIComponent(limit)}`,
      { method: 'GET' },
    );
  }

  async listLandingOperationRuns(packageId: string, scope: OpsExecutionScope = 'admin', limit = 200): Promise<ApiResponse<OpsLandingOperationRun[]>> {
    return await opsRequest<OpsLandingOperationRun[]>(
      `${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/landing-operation-runs?limit=${encodeURIComponent(limit)}`,
      { method: 'GET' },
    );
  }

  async listLandingEvents(packageId: string, scope: OpsExecutionScope = 'user', limit = 100): Promise<ApiResponse<OpsChangePackageEvent[]>> {
    return await opsRequest<OpsChangePackageEvent[]>(
      `${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/landing-events?limit=${encodeURIComponent(limit)}`,
      { method: 'GET' },
    );
  }

  async create(request: Record<string, any>): Promise<ApiResponse<OpsChangePackage>> {
    return await opsRequest<OpsChangePackage>(this.adminBaseUrl, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async createFromSession(sessionId: string, request: Record<string, any>): Promise<ApiResponse<OpsChangePackage>> {
    return await opsRequest<OpsChangePackage>(
      `${API_CONFIG.BASE_DOMAIN}/api/v1/user/chat/sessions/${encodeURIComponent(sessionId)}/change-packages`,
      { method: 'POST', body: JSON.stringify(request) },
    );
  }

  async revise(packageId: string, request: Record<string, any>, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<OpsChangePackage>> {
    return await opsRequest<OpsChangePackage>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/revise`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async validate(packageId: string, request: Record<string, any> = {}, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<Record<string, any>>> {
    return await opsRequest<Record<string, any>>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/validate`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async submitReview(packageId: string, request: Record<string, any> = {}, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<Record<string, any>>> {
    return await opsRequest<Record<string, any>>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/submit-review`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async approve(packageId: string, version: number, packageHash: string, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<OpsChangePackage>> {
    return await opsRequest<OpsChangePackage>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/approve`, {
      method: 'POST',
      body: JSON.stringify({ version, packageHash }),
    });
  }

  async listApprovalChannels(
    packageId: string,
    scope: OpsExecutionScope = 'admin',
  ): Promise<ApiResponse<OpsApprovalChannelOption[]>> {
    return await opsRequest<OpsApprovalChannelOption[]>(
      `${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/approval-channels`,
      { method: 'GET' },
    );
  }

  async sendApprovalCard(
    packageId: string,
    channelId: string,
    target: string,
    scope: OpsExecutionScope = 'admin',
  ): Promise<ApiResponse<Record<string, any>>> {
    return await opsRequest<Record<string, any>>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/approval-card`, {
      method: 'POST',
      body: JSON.stringify({ channelId, target }),
    });
  }

  async reject(packageId: string, request: Record<string, any>, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<OpsChangePackage>> {
    return await opsRequest<OpsChangePackage>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/reject`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async getLandingPlan(packageId: string, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<Record<string, any>>> {
    return await opsRequest<Record<string, any>>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/landing-plan`, { method: 'GET' });
  }

  async land(packageId: string, request: Record<string, any> = {}, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<OpsChangePackage>> {
    return await opsRequest<OpsChangePackage>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/land`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async cleanup(packageId: string, request: Record<string, any> = {}, scope: OpsExecutionScope = 'admin'): Promise<ApiResponse<Record<string, any>>> {
    return await opsRequest<Record<string, any>>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/cleanup`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async verifyLanding(packageId: string, scope: OpsExecutionScope): Promise<ApiResponse<Record<string, any>>> {
    return await opsRequest<Record<string, any>>(`${this.baseUrl(scope)}/${encodeURIComponent(packageId)}/verify-landing`, {
      method: 'POST', body: JSON.stringify({}),
    });
  }

  async listApprovedValidationScripts(packageId: string, version: number, packageHash: string): Promise<ApiResponse<Record<string, any>[]>> {
    const query = new URLSearchParams({ version: String(version), packageHash });
    return await opsRequest(`${this.adminBaseUrl}/${encodeURIComponent(packageId)}/approved-validation-scripts?${query.toString()}`, { method: 'GET' });
  }

  async createApprovedValidationScript(packageId: string, payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
    return await opsRequest(`${this.adminBaseUrl}/${encodeURIComponent(packageId)}/approved-validation-scripts`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  async updateApprovedValidationScriptStatus(packageId: string, scriptId: string, payload: Record<string, any>): Promise<ApiResponse<Record<string, any>>> {
    return await opsRequest(
      `${this.adminBaseUrl}/${encodeURIComponent(packageId)}/approved-validation-scripts/${encodeURIComponent(scriptId)}/status`,
      { method: 'PATCH', body: JSON.stringify(payload) },
    );
  }
}

export const opsChangePackageService = new OpsChangePackageService();
