import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

export interface OpsProjectResourceTemplate {
  type: string;
  name: string;
  category: string;
  actions: string[];
  schemaHint: string;
  multiObjectPermission?: OpsProjectMultiObjectPermission;
}

export interface OpsProjectResourceSchemaObject {
  name: string;
  comment?: string;
  columns?: string[];
  indexes?: string[];
  dataType?: string;
  fields?: string[];
  labels?: string[];
}

export interface OpsProjectResourcePermission {
  resourceId?: string;
  actions: string[];
  objects: string[];
  maxRows?: number;
  timeoutSeconds?: number;
  allowJoin?: boolean;
  multiObjectPermission?: OpsProjectMultiObjectPermission;
  maxRangeMinutes?: number;
}

export interface OpsProjectMultiObjectPermission {
  enabled?: boolean;
  key?: string;
  label?: string;
  description?: string;
}

export interface OpsProjectResource {
  resourceId: string;
  projectId: string;
  type: string;
  typeName: string;
  name: string;
  environment: string;
  endpoint: string;
  status: string;
  credential?: {
    username?: string;
    configured?: boolean;
    passwordMasked?: string;
    passwordRef?: string;
    updatedAt?: string;
  };
  schema: {
    objects: OpsProjectResourceSchemaObject[];
    scannedAt?: string;
    source?: string;
    message?: string;
  };
  permission: OpsProjectResourcePermission;
  createdAt?: string;
}

export interface OpsGeneratedMcp {
  mcpId: string;
  toolId?: string;
  mcpName: string;
  toolName?: string;
  projectId: string;
  resourceId: string;
  resourceType: string;
  templateId?: string;
  transportType: string;
  transportConfig: Record<string, unknown>;
  allowedActions?: string[];
  riskLevel?: string;
  readOnly?: boolean;
  permissionPolicy?: Record<string, unknown>;
  requestTimeout: number;
  status: string;
  createdAt: string;
  updatedAt?: string;
}

export interface OpsDiagnosticScenario {
  scenarioId: string;
  name: string;
  description: string;
  promptTemplate: string;
  ready: boolean;
  unavailableReason?: string;
  allowedSources?: string[];
}

export interface OpsProjectReadinessCheck {
  key: string;
  label: string;
  ready: boolean;
  level?: 'NOT_CONFIGURED' | 'CONFIGURED' | 'CONNECTIVITY_VERIFIED' | 'QUERY_VERIFIED' | 'AUTHORIZED' | 'ENVIRONMENT_VALIDATED';
  detail?: string;
}

export interface OpsProjectReadiness {
  ready: boolean;
  checks: OpsProjectReadinessCheck[];
  missing: string[];
  nextAction: string;
}

export interface OpsProjectWorkspace {
  projectId: string;
  name: string;
  description: string;
  owner: string;
  environments: string[];
  knowledgeBaseId?: string;
  knowledgeBaseIds?: string[];
  projectKnowledgeBases?: Array<Record<string, any>>;
  enabledGlobalKnowledgeBases?: Array<Record<string, any>>;
  defaultAgentId?: string;
  skillIds?: string[];
  projectSkills?: Array<Record<string, any>>;
  enabledGlobalSkills?: Array<Record<string, any>>;
  sharedMcpIds?: string[];
  createdAt?: string;
  resources: OpsProjectResource[];
  generatedMcps: OpsGeneratedMcp[];
  resourceCount: number;
  generatedMcpCount: number;
  dataResourceCount?: number;
  sourceRepositoryCount?: number;
  executionResourceCount?: number;
  readyForInvestigation?: boolean;
  defaultAgentPublished?: boolean;
  readinessReason?: 'READY' | 'DEFAULT_AGENT_NOT_CONFIGURED' | 'DEFAULT_AGENT_NOT_PUBLISHED' | 'NO_RESOURCE_CONNECTED';
  diagnosisReadiness?: OpsProjectReadiness;
  remediationReadiness?: OpsProjectReadiness;
  onboarding?: Array<{ key: string; label: string; completed: boolean; optional?: boolean }>;
  diagnosticScenarios?: OpsDiagnosticScenario[];
  recommendedScenarioId?: string;
}

export interface OpsProjectSnapshot {
  projects: OpsProjectWorkspace[];
  templates: OpsProjectResourceTemplate[];
}

export interface OpsProjectMember {
  project_id?: string;
  projectId?: string;
  member_key?: string;
  memberKey?: string;
  user_id?: string;
  userId?: string;
  username?: string;
  member_role?: string;
  memberRole?: string;
  status?: string;
  granted_by?: string;
  grantedBy?: string;
  create_time?: string;
  update_time?: string;
}

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

class OpsProjectService {
  private baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops-projects`;
  private opsBaseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops`;

  async snapshot(): Promise<ApiResponse<OpsProjectSnapshot>> {
    return this.request('/snapshot');
  }

  async templates(): Promise<ApiResponse<OpsProjectResourceTemplate[]>> {
    return this.request('/templates');
  }

  async createProject(request: Record<string, unknown>): Promise<ApiResponse<OpsProjectWorkspace>> {
    return this.request('/projects', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async updateProject(request: Record<string, unknown>): Promise<ApiResponse<OpsProjectWorkspace>> {
    return this.request('/projects/update', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async ensureDefaultAgent(projectId: string): Promise<ApiResponse<OpsProjectWorkspace>> {
    return this.request(`/projects/${encodeURIComponent(projectId)}/default-agent/ensure`, {
      method: 'POST',
    });
  }

  async listProjectMembers(projectId: string): Promise<ApiResponse<OpsProjectMember[]>> {
    return this.request(`/projects/${encodeURIComponent(projectId)}/members`);
  }

  async replaceProjectMembers(
    projectId: string,
    members: Array<{ userId?: string; username?: string; memberRole?: string }>,
  ): Promise<ApiResponse<OpsProjectMember[]>> {
    return this.request(`/projects/${encodeURIComponent(projectId)}/members`, {
      method: 'PUT',
      body: JSON.stringify({ members }),
    });
  }

  async addResource(request: Record<string, unknown>): Promise<ApiResponse<OpsProjectWorkspace>> {
    return this.request('/resources', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async updateResource(request: Record<string, unknown>): Promise<ApiResponse<OpsProjectWorkspace>> {
    return this.request('/resources/update', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async updatePermission(request: Record<string, unknown>): Promise<ApiResponse<OpsProjectWorkspace>> {
    return this.request('/resources/permission', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async generateMcp(request: Record<string, unknown>): Promise<ApiResponse<OpsProjectWorkspace>> {
    return this.request('/mcps/generate', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async listProjectTools(projectId: string): Promise<ApiResponse<OpsGeneratedMcp[]>> {
    return this.opsRequest(`/projects/${encodeURIComponent(projectId)}/tools`);
  }

  async generateProjectTool(projectId: string, request: Record<string, unknown>): Promise<ApiResponse<OpsGeneratedMcp>> {
    return this.opsRequest(`/projects/${encodeURIComponent(projectId)}/tools/generate-from-template`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async updateProjectTool(projectId: string, toolId: string, request: Record<string, unknown>): Promise<ApiResponse<OpsGeneratedMcp>> {
    return this.opsRequest(`/projects/${encodeURIComponent(projectId)}/tools/${encodeURIComponent(toolId)}`, {
      method: 'PUT',
      body: JSON.stringify(request),
    });
  }

  async updateProjectToolStatus(projectId: string, toolId: string, status: string): Promise<ApiResponse<OpsGeneratedMcp>> {
    return this.opsRequest(`/projects/${encodeURIComponent(projectId)}/tools/${encodeURIComponent(toolId)}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
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
    return response.json();
  }

  private async opsRequest<T>(path: string, init?: RequestInit): Promise<ApiResponse<T>> {
    const response = await fetch(`${this.opsBaseUrl}${path}`, {
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
    return response.json();
  }
}

export const opsProjectService = new OpsProjectService();
