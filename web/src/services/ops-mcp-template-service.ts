import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export interface OpsMcpTemplate {
  id?: number;
  templateId: string;
  mcpTemplateId?: string;
  templateName: string;
  name?: string;
  resourceType: string;
  transportType: string;
  defaultTransportConfig: Record<string, unknown>;
  supportedActions: string[];
  riskLevel: string;
  readOnly: boolean;
  description?: string;
  status: string;
  createBy?: string;
  projectCount?: number;
  generatedToolCount?: number;
  generatedTools?: OpsProjectTool[];
  createTime?: string;
  updateTime?: string;
}

export interface OpsProjectTool {
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
  requestTimeout?: number;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OpsMcpTemplateRequest {
  templateId?: string;
  templateName?: string;
  resourceType?: string;
  transportType?: string;
  defaultTransportConfig?: Record<string, unknown>;
  supportedActions?: string[];
  riskLevel?: string;
  readOnly?: boolean;
  description?: string;
  status?: string;
}

class OpsMcpTemplateService {
  private baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ops`;

  async listTemplates(): Promise<ApiResponse<OpsMcpTemplate[]>> {
    return this.request('/mcp-templates');
  }

  async createTemplate(request: OpsMcpTemplateRequest): Promise<ApiResponse<OpsMcpTemplate>> {
    return this.request('/mcp-templates', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async updateTemplate(templateId: string, request: OpsMcpTemplateRequest): Promise<ApiResponse<OpsMcpTemplate>> {
    return this.request(`/mcp-templates/${encodeURIComponent(templateId)}`, {
      method: 'PUT',
      body: JSON.stringify(request),
    });
  }

  async updateTemplateStatus(templateId: string, status: string): Promise<ApiResponse<OpsMcpTemplate>> {
    return this.request(`/mcp-templates/${encodeURIComponent(templateId)}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  }

  async copyTemplate(templateId: string, request: OpsMcpTemplateRequest = {}): Promise<ApiResponse<OpsMcpTemplate>> {
    return this.request(`/mcp-templates/${encodeURIComponent(templateId)}/copy`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async generatedTools(templateId: string): Promise<ApiResponse<OpsProjectTool[]>> {
    return this.request(`/mcp-templates/${encodeURIComponent(templateId)}/generated-tools`);
  }

  async listProjectTools(projectId: string): Promise<ApiResponse<OpsProjectTool[]>> {
    return this.request(`/projects/${encodeURIComponent(projectId)}/tools`);
  }

  async generateProjectTool(projectId: string, request: Record<string, unknown>): Promise<ApiResponse<OpsProjectTool>> {
    return this.request(`/projects/${encodeURIComponent(projectId)}/tools/generate-from-template`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async updateProjectTool(projectId: string, toolId: string, request: Record<string, unknown>): Promise<ApiResponse<OpsProjectTool>> {
    return this.request(`/projects/${encodeURIComponent(projectId)}/tools/${encodeURIComponent(toolId)}`, {
      method: 'PUT',
      body: JSON.stringify(request),
    });
  }

  async updateProjectToolStatus(projectId: string, toolId: string, status: string): Promise<ApiResponse<OpsProjectTool>> {
    return this.request(`/projects/${encodeURIComponent(projectId)}/tools/${encodeURIComponent(toolId)}/status`, {
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
}

export const opsMcpTemplateService = new OpsMcpTemplateService();
