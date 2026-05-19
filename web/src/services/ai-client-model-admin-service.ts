/**
 * 模型目录管理 API 服务
 */

import { API_CONFIG, API_ENDPOINTS, DEFAULT_HEADERS } from '../config';

// 定义请求数据类型
export interface AiClientModelRequestDTO {
  id?: number;
  modelId: string;
  modelName: string;
  modelUsage: string;
  modelType?: string;
  apiId?: string;
  description?: string;
  status: number;
}

// 定义查询请求数据类型
export interface AiClientModelQueryRequestDTO {
  modelId?: string;
  apiId?: string;
  modelType?: string;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}

// 定义响应数据类型
export interface AiClientModelResponseDTO {
  id: number;
  modelId: string;
  modelName: string;
  modelUsage: string;
  modelType?: string;
  apiId?: string;
  description?: string;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface ModelDefaultPolicy {
  id?: number;
  projectId?: string;
  scope?: 'GLOBAL' | 'PROJECT' | string;
  defaultChatModelId?: string;
  defaultEmbeddingModelId?: string;
  defaultRerankModelId?: string;
  defaultVisionModelId?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AiClientModelSyncResponseDTO {
  apiId?: string;
  endpoint?: string;
  httpStatus?: number;
  fetchedCount?: number;
  createdCount?: number;
  updatedCount?: number;
  skippedCount?: number;
  modelIds?: string[];
  errorMessage?: string;
  syncedAt?: string;
}

// 定义API响应格式
export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

/**
 * 模型目录管理 API 服务类
 */
export class AiClientModelAdminService {
  private static readonly BASE_URL = API_ENDPOINTS.AI_CLIENT_MODEL.BASE;

  /**
   * 创建模型配置
   */
  static async createAiClientModel(request: AiClientModelRequestDTO): Promise<ApiResponse<boolean>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.CREATE}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('创建模型配置失败:', error);
      throw error;
    }
  }

  /**
   * 根据 ID 更新模型配置
   */
  static async updateAiClientModelById(request: AiClientModelRequestDTO): Promise<ApiResponse<boolean>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.UPDATE_BY_ID}`, {
        method: 'PUT',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据 ID 更新模型配置失败:', error);
      throw error;
    }
  }

  /**
   * 根据模型 ID 更新模型配置
   */
  static async updateAiClientModelByModelId(request: AiClientModelRequestDTO): Promise<ApiResponse<boolean>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.UPDATE_BY_MODEL_ID}`, {
        method: 'PUT',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据模型 ID 更新模型配置失败:', error);
      throw error;
    }
  }

  /**
   * 根据 ID 删除模型配置
   */
  static async deleteAiClientModelById(id: number): Promise<ApiResponse<boolean>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.DELETE_BY_ID}/${id}`, {
        method: 'DELETE',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据 ID 删除模型配置失败:', error);
      throw error;
    }
  }

  /**
   * 根据模型 ID 删除模型配置
   */
  static async deleteAiClientModelByModelId(modelId: string): Promise<ApiResponse<boolean>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.DELETE_BY_MODEL_ID}/${modelId}`, {
        method: 'DELETE',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据模型 ID 删除模型配置失败:', error);
      throw error;
    }
  }

  /**
   * 根据 ID 查询模型配置
   */
  static async queryAiClientModelById(id: number): Promise<ApiResponse<AiClientModelResponseDTO>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.QUERY_BY_ID}/${id}`, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据 ID 查询模型配置失败:', error);
      throw error;
    }
  }

  /**
   * 根据模型 ID 查询模型配置
   */
  static async queryAiClientModelByModelId(modelId: string): Promise<ApiResponse<AiClientModelResponseDTO>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.QUERY_BY_MODEL_ID}/${modelId}`, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据模型 ID 查询模型配置失败:', error);
      throw error;
    }
  }

  /**
   * 根据 API 配置 ID 查询模型配置列表
   */
  static async queryAiClientModelsByApiId(apiId: string): Promise<ApiResponse<AiClientModelResponseDTO[]>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.QUERY_BY_API_ID}/${apiId}`, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据 API 配置 ID 查询模型配置列表失败:', error);
      throw error;
    }
  }

  /**
   * 根据模型类型查询模型配置列表
   */
  static async queryAiClientModelsByModelType(modelType: string): Promise<ApiResponse<AiClientModelResponseDTO[]>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.QUERY_BY_MODEL_TYPE}/${modelType}`, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据模型类型查询模型配置列表失败:', error);
      throw error;
    }
  }

  /**
   * 查询所有启用的模型配置
   */
  static async queryEnabledAiClientModels(): Promise<ApiResponse<AiClientModelResponseDTO[]>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.QUERY_ENABLED}`, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('查询所有启用的模型配置失败:', error);
      throw error;
    }
  }

  /**
   * 根据条件查询模型配置列表
   */
  static async queryAiClientModelList(request: AiClientModelQueryRequestDTO): Promise<ApiResponse<AiClientModelResponseDTO[]>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.QUERY_LIST}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('根据条件查询模型配置列表失败:', error);
      throw error;
    }
  }

  /**
   * 查询所有模型配置
   */
  static async queryAllAiClientModels(): Promise<ApiResponse<AiClientModelResponseDTO[]>> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_MODEL.QUERY_ALL}`, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('查询所有模型配置失败:', error);
      throw error;
    }
  }

  static async syncFromProvider(apiId: string): Promise<ApiResponse<AiClientModelSyncResponseDTO>> {
    const response = await fetch(`${this.BASE_URL}/sync-from-provider/${encodeURIComponent(apiId)}`, {
      method: 'POST',
      headers: DEFAULT_HEADERS,
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  static async getDefaultPolicy(projectId?: string): Promise<ApiResponse<ModelDefaultPolicy>> {
    const params = new URLSearchParams();
    if (projectId) params.set('projectId', projectId);
    const query = params.toString();
    const response = await fetch(
      `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/model-default-policy${query ? `?${query}` : ''}`,
      {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      },
    );
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  static async updateDefaultPolicy(
    policy: ModelDefaultPolicy,
    projectId?: string,
  ): Promise<ApiResponse<ModelDefaultPolicy>> {
    const params = new URLSearchParams();
    if (projectId) params.set('projectId', projectId);
    const query = params.toString();
    const response = await fetch(
      `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/model-default-policy${query ? `?${query}` : ''}`,
      {
        method: 'PUT',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(policy),
      },
    );
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }
}

// 导出服务实例
export const aiClientModelAdminService = AiClientModelAdminService;
