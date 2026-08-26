import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export interface OpsSourceRepository {
  repositoryId: string;
  projectId: string;
  name: string;
  localPath: string;
  defaultRevision: string;
  defaultCommitSha: string;
  status: string;
}

export interface OpsProjectService {
  serviceId: string;
  projectId: string;
  name: string;
  repositoryId: string;
  modulePath: string;
  buildProfile: string;
  artifactPath: string;
  deploymentResourceId: string;
  healthUrl: string;
  smokeUrls: string[];
  status: string;
}

export interface OpsExecutionResource {
  resourceId: string;
  projectId: string;
  name: string;
  workerId: string;
  adapter: 'local-java-service' | 'deployment-http' | 'mysql-controlled' | 'redis-controlled' | 'rabbitmq-policy';
  adapterTemplateId?: string;
  environments: string[];
  configuration: Record<string, unknown>;
  status: 'ENABLED' | 'DISABLED';
  createdAt?: string;
  updatedAt?: string;
}

export interface OpsExecutionAdapterTemplate {
  id?: number;
  adapterTemplateId: string;
  templateId?: string;
  templateName: string;
  name?: string;
  adapterType: OpsExecutionResource['adapter'] | string;
  resourceType?: string;
  supportedActions: string[];
  defaultConfig?: Record<string, unknown>;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  readOnly: boolean;
  description?: string;
  status: 'ENABLED' | 'DISABLED' | string;
  createBy?: string;
  generatedTargetCount?: number;
  generatedTargets?: Array<Record<string, unknown>>;
  createTime?: string;
  updateTime?: string;
}

export interface OpsRepairWorkspace {
  workspaceId: string;
  projectId: string;
  serviceId: string;
  repositoryId: string;
  environment: string;
  baseCommit: string;
  verifiedCommit: string;
  status: string;
  summary: string;
  changedFiles: string[];
  testProfile: string;
  testCommand: string;
  testExitCode: number;
  testLog: string;
  artifactPath: string;
  artifactSha256: string;
  artifactSize: number;
  createdBy: string;
  createdAt: string;
}

export interface OpsCodeDelivery {
  deliveryId: string;
  workspaceId: string;
  mode: string;
  branchName: string;
  commitSha: string;
  pullRequestUrl: string;
  ciStatus: string;
  ciUrl: string;
  createdAt: string;
}

class OpsRepairService {
  private sourceBase = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/source-repositories`;
  private repairBase = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/repair-workspaces`;
  private opsBase = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops`;

  listRepositories(projectId?: string): Promise<ApiResponse<OpsSourceRepository[]>> {
    return this.request(`${this.sourceBase}${projectId ? `?projectId=${encodeURIComponent(projectId)}` : ''}`);
  }

  getRepositoryCapabilities(): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request(`${this.sourceBase}/capabilities`);
  }

  registerRepository(payload: Record<string, unknown>): Promise<ApiResponse<OpsSourceRepository>> {
    return this.request(this.sourceBase, { method: 'POST', body: JSON.stringify(payload) });
  }

  listServices(projectId?: string): Promise<ApiResponse<OpsProjectService[]>> {
    const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';
    return this.request(`${this.sourceBase}/services${query}`);
  }

  getServiceCapabilities(): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request(`${this.sourceBase}/services/capabilities`);
  }

  saveService(payload: Record<string, unknown>): Promise<ApiResponse<OpsProjectService>> {
    return this.request(`${this.sourceBase}/services`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  listExecutionResources(projectId?: string): Promise<ApiResponse<OpsExecutionResource[]>> {
    const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';
    return this.request(
      `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/execution-resources${query}`,
    );
  }

  getExecutionResourceCapabilities(): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request(`${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/execution-resources/capabilities`);
  }

  saveExecutionResource(payload: Record<string, unknown>): Promise<ApiResponse<OpsExecutionResource>> {
    return this.request(
      `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops/execution-resources`,
      { method: 'POST', body: JSON.stringify(payload) },
    );
  }

  listExecutionAdapterTemplates(): Promise<ApiResponse<OpsExecutionAdapterTemplate[]>> {
    return this.request(`${this.opsBase}/execution-adapter-templates`);
  }

  createExecutionAdapterTemplate(payload: Partial<OpsExecutionAdapterTemplate>): Promise<ApiResponse<OpsExecutionAdapterTemplate>> {
    return this.request(`${this.opsBase}/execution-adapter-templates`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  updateExecutionAdapterTemplate(
    adapterTemplateId: string,
    payload: Partial<OpsExecutionAdapterTemplate>
  ): Promise<ApiResponse<OpsExecutionAdapterTemplate>> {
    return this.request(`${this.opsBase}/execution-adapter-templates/${encodeURIComponent(adapterTemplateId)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  updateExecutionAdapterTemplateStatus(
    adapterTemplateId: string,
    status: 'ENABLED' | 'DISABLED' | string
  ): Promise<ApiResponse<OpsExecutionAdapterTemplate>> {
    return this.request(`${this.opsBase}/execution-adapter-templates/${encodeURIComponent(adapterTemplateId)}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  }

  copyExecutionAdapterTemplate(
    adapterTemplateId: string,
    payload: Record<string, unknown> = {}
  ): Promise<ApiResponse<OpsExecutionAdapterTemplate>> {
    return this.request(`${this.opsBase}/execution-adapter-templates/${encodeURIComponent(adapterTemplateId)}/copy`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  listExecutionAdapterTemplateTargets(adapterTemplateId: string): Promise<ApiResponse<Array<Record<string, unknown>>>> {
    return this.request(`${this.opsBase}/execution-adapter-templates/${encodeURIComponent(adapterTemplateId)}/generated-targets`);
  }

  listProjectExecutionTargets(projectId: string): Promise<ApiResponse<OpsExecutionResource[]>> {
    return this.request(`${this.opsBase}/projects/${encodeURIComponent(projectId)}/execution-targets`);
  }

  generateProjectExecutionTarget(
    projectId: string,
    payload: Record<string, unknown>
  ): Promise<ApiResponse<OpsExecutionResource>> {
    return this.request(`${this.opsBase}/projects/${encodeURIComponent(projectId)}/execution-targets/generate-from-template`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  updateProjectExecutionTarget(
    projectId: string,
    targetId: string,
    payload: Record<string, unknown>
  ): Promise<ApiResponse<OpsExecutionResource>> {
    return this.request(`${this.opsBase}/projects/${encodeURIComponent(projectId)}/execution-targets/${encodeURIComponent(targetId)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  }

  updateProjectExecutionTargetStatus(
    projectId: string,
    targetId: string,
    status: 'ENABLED' | 'DISABLED' | string
  ): Promise<ApiResponse<OpsExecutionResource>> {
    return this.request(`${this.opsBase}/projects/${encodeURIComponent(projectId)}/execution-targets/${encodeURIComponent(targetId)}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  }

  listWorkspaces(projectId?: string): Promise<ApiResponse<OpsRepairWorkspace[]>> {
    const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';
    return this.request(`${this.repairBase}${query}`);
  }

  createWorkspace(payload: Record<string, unknown>): Promise<ApiResponse<OpsRepairWorkspace>> {
    return this.request(this.repairBase, { method: 'POST', body: JSON.stringify(payload) });
  }

  publishDelivery(workspaceId: string, payload: Record<string, unknown>): Promise<ApiResponse<OpsCodeDelivery>> {
    return this.request(`${this.repairBase}/${encodeURIComponent(workspaceId)}/deliveries`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  listDeliveries(workspaceId: string): Promise<ApiResponse<OpsCodeDelivery[]>> {
    return this.request(`${this.repairBase}/${encodeURIComponent(workspaceId)}/deliveries`);
  }

  private async request<T>(url: string, init?: RequestInit): Promise<ApiResponse<T>> {
    const response = await fetch(url, {
      method: 'GET',
      ...init,
      headers: { ...DEFAULT_HEADERS, ...(init?.headers || {}) },
    });
    const envelope = await response.json().catch(() => ({ code: '500', info: `HTTP ${response.status}` }));
    if (!response.ok || envelope.code !== '0000') {
      throw new Error(envelope.info || `HTTP ${response.status}`);
    }
    return envelope;
  }
}

export const opsRepairService = new OpsRepairService();
