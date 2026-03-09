import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

export interface OpsChannel {
  channelId: string;
  projectId: string;
  executionType: 'NONE' | 'REACT' | 'WORKFLOW';
  workflowId?: string;
  workflowVersionPolicy?: 'LATEST_PUBLISHED' | 'PINNED_VERSION';
  workflowVersion?: number;
  workflowDefinitionHash?: string;
  /** @deprecated compatibility aliases for older API consumers */
  agentId?: string;
  agentBindingMode?: 'LATEST_PUBLISHED' | 'PINNED_VERSION';
  agentVersion?: number;
  agentDefinitionHash?: string;
  name: string;
  type: 'GENERIC_WEBHOOK';
  credentialRef: string;
  config: { outboundUrl?: string; timeoutSeconds?: number };
  status: 'ACTIVE' | 'DISABLED';
  createTime?: string;
  updateTime?: string;
}

export interface OpsChannelMessageRecord {
  messageId: string;
  channelId: string;
  projectId: string;
  externalMessageId?: string;
  externalConversationId?: string;
  senderId?: string;
  sessionId?: string;
  runId?: string;
  direction: 'INBOUND' | 'OUTBOUND';
  status: string;
  errorMessage?: string;
  createTime?: string;
}

export interface OpsChannelIdentity {
  mappingId: string;
  channelId: string;
  projectId: string;
  externalSenderId: string;
  platformUserId: string;
  username: string;
  status: 'ACTIVE' | 'DISABLED';
  version: number;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
}

export interface OpsChannelOutboxItem {
  id: number;
  projectId: string;
  channelId: string;
  target: string;
  messageType: 'CHAT_REPLY' | 'ANALYSIS_NOTIFICATION';
  referenceId: string;
  status: 'PENDING' | 'RUNNING' | 'FAILED' | 'SUCCEEDED' | 'DEAD_LETTER' | 'CANCELLED';
  retryCount: number;
  lastError?: string;
  nextRetryAt?: string;
  createTime?: string;
  updateTime?: string;
}

interface ApiResponse<T> { code: string; info: string; data: T }

class OpsChannelService {
  private readonly baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/channels`;
  private readonly notificationBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/channel-notifications`;

  async list(projectId: string): Promise<ApiResponse<OpsChannel[]>> {
    return this.request(`?projectId=${encodeURIComponent(projectId)}`);
  }

  async create(payload: Record<string, unknown>): Promise<ApiResponse<OpsChannel>> {
    return this.request('', { method: 'POST', body: JSON.stringify(payload) });
  }

  async update(projectId: string, channelId: string, payload: Record<string, unknown>): Promise<ApiResponse<OpsChannel>> {
    return this.request(`/${encodeURIComponent(channelId)}?projectId=${encodeURIComponent(projectId)}`, {
      method: 'PUT', body: JSON.stringify(payload),
    });
  }

  async messages(projectId: string, channelId: string, limit = 50): Promise<ApiResponse<OpsChannelMessageRecord[]>> {
    return this.request(`/${encodeURIComponent(channelId)}/messages?projectId=${encodeURIComponent(projectId)}&limit=${limit}`);
  }

  async identities(projectId: string, channelId: string): Promise<ApiResponse<OpsChannelIdentity[]>> {
    return this.request(`/${encodeURIComponent(channelId)}/identities?projectId=${encodeURIComponent(projectId)}`);
  }

  async requeueInboundRecovery(projectId: string, channelId: string, externalMessageId: string): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request(`/${encodeURIComponent(channelId)}/messages/${encodeURIComponent(externalMessageId)}/recovery/requeue?projectId=${encodeURIComponent(projectId)}`, {
      method: 'POST', body: JSON.stringify({ confirmedNoSideEffect: true }),
    });
  }

  async cancelInboundRecovery(projectId: string, channelId: string, externalMessageId: string): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request(`/${encodeURIComponent(channelId)}/messages/${encodeURIComponent(externalMessageId)}/recovery/cancel?projectId=${encodeURIComponent(projectId)}`, {
      method: 'POST',
    });
  }

  async bindIdentity(
    projectId: string,
    channelId: string,
    payload: {
      externalSenderId: string;
      platformUserId: string;
      username: string;
      status: 'ACTIVE' | 'DISABLED';
      expectedVersion?: number;
    },
  ): Promise<ApiResponse<OpsChannelIdentity>> {
    return this.request(`/${encodeURIComponent(channelId)}/identities?projectId=${encodeURIComponent(projectId)}`, {
      method: 'PUT', body: JSON.stringify(payload),
    });
  }

  async send(projectId: string, channelId: string, target: string, content: string): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request(`/${encodeURIComponent(channelId)}/send?projectId=${encodeURIComponent(projectId)}`, {
      method: 'POST', body: JSON.stringify({ target, content }),
    });
  }

  async outbox(projectId: string, limit = 100): Promise<ApiResponse<OpsChannelOutboxItem[]>> {
    return this.notificationRequest(`/outbox?projectId=${encodeURIComponent(projectId)}&limit=${limit}`);
  }

  async processOutbox(limit = 20): Promise<ApiResponse<Record<string, unknown>>> {
    return this.notificationRequest(`/outbox/process?limit=${limit}`, { method: 'POST' });
  }

  async requeueOutbox(projectId: string, id: number): Promise<ApiResponse<OpsChannelOutboxItem>> {
    return this.notificationRequest(`/outbox/${id}/requeue?projectId=${encodeURIComponent(projectId)}`, { method: 'POST' });
  }

  async cancelOutbox(projectId: string, id: number): Promise<ApiResponse<OpsChannelOutboxItem>> {
    return this.notificationRequest(`/outbox/${id}/cancel?projectId=${encodeURIComponent(projectId)}`, { method: 'POST' });
  }

  private async request<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
    return this.requestAt(this.baseUrl, path, init);
  }

  private async notificationRequest<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
    return this.requestAt(this.notificationBaseUrl, path, init);
  }

  private async requestAt<T>(baseUrl: string, path: string, init?: RequestInit): Promise<ApiResponse<T>> {
    const response = await fetch(`${baseUrl}${path}`, {
      method: 'GET', ...init, headers: { ...DEFAULT_HEADERS, ...(init?.headers || {}) },
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    if (payload.code !== '0000') throw new Error(payload.info || '渠道请求失败');
    return payload;
  }
}

export const opsChannelService = new OpsChannelService();
