import { API_CONFIG } from '../config/api';
import { ApiResponse, opsRequest } from './ops-http-client';

export type OpsWorkflowApprovalScope = 'admin' | 'user';
export type OpsWorkflowApprovalStatus = 'WAITING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

export interface OpsWorkflowApprovalView {
  available: boolean;
  runId: string;
  projectId: string;
  runStatus: string;
  approvalId?: string;
  nodeId?: string;
  status?: OpsWorkflowApprovalStatus;
  requestSummary?: string;
  requestedAt?: string;
  expiresAt?: string;
  decidedBy?: string;
  decidedAt?: string;
  channelBound?: boolean;
  canDecide?: boolean;
  resumeRequired?: boolean;
}

class OpsWorkflowApprovalService {
  private baseUrl(scope: OpsWorkflowApprovalScope): string {
    return scope === 'user'
      ? `${API_CONFIG.BASE_DOMAIN}/api/v1/user/chat`
      : `${API_CONFIG.BASE_DOMAIN}/api/v1/agent/chat`;
  }

  async get(
    runId: string,
    projectId: string,
    scope: OpsWorkflowApprovalScope = 'admin',
  ): Promise<ApiResponse<OpsWorkflowApprovalView>> {
    const params = new URLSearchParams({ projectId });
    return await opsRequest<OpsWorkflowApprovalView>(
      `${this.baseUrl(scope)}/runs/${encodeURIComponent(runId)}/workflow-approval?${params.toString()}`,
      { method: 'GET' },
    );
  }

  async decide(
    runId: string,
    projectId: string,
    decision: 'APPROVE' | 'REJECT',
    approvalId: string,
    scope: OpsWorkflowApprovalScope = 'admin',
  ): Promise<ApiResponse<OpsWorkflowApprovalView>> {
    const params = new URLSearchParams({ projectId });
    return await opsRequest<OpsWorkflowApprovalView>(
      `${this.baseUrl(scope)}/runs/${encodeURIComponent(runId)}/workflow-approval/decision?${params.toString()}`,
      { method: 'POST', body: JSON.stringify({ decision, approvalId }) },
    );
  }

  async retryResume(
    runId: string,
    projectId: string,
    approvalId: string,
    scope: OpsWorkflowApprovalScope = 'admin',
  ): Promise<ApiResponse<OpsWorkflowApprovalView>> {
    const params = new URLSearchParams({ projectId });
    return await opsRequest<OpsWorkflowApprovalView>(
      `${this.baseUrl(scope)}/runs/${encodeURIComponent(runId)}/workflow-approval/resume?${params.toString()}`,
      { method: 'POST', body: JSON.stringify({ approvalId }) },
    );
  }
}

export const opsWorkflowApprovalService = new OpsWorkflowApprovalService();
