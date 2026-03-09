import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

// 请求和响应接口定义
export interface AiClientRagOrderRequestDTO {
  id?: number;
  ragId?: string;
  ragName?: string;
  knowledgeTag?: string;
  status?: number;
}

export interface AiClientRagOrderQueryRequestDTO {
  ragId?: string;
  ragName?: string;
  knowledgeTag?: string;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}

export interface AiClientRagOrderResponseDTO {
  id: number;
  ragId: string;
  ragName: string;
  knowledgeTag: string;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface RagDocumentResponseDTO {
  fileName: string;
  displayName: string;
  tag: string;
  size: number;
  updateTime: string;
  previewable: boolean;
  content?: string;
  chunkId?: string;
  chunkIndex?: number;
  source?: string;
  documentType?: string;
  chunkStrategy?: string;
}

export interface RagIngestionJobRecord {
  jobId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | string;
  name: string;
  tag: string;
  fileNames: string[];
  totalBytes?: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
  metadata?: Record<string, any>;
}

export interface RagKnowledgeBaseSummary {
  knowledgeTag: string;
  kbId?: string;
  kbName?: string;
  ragId?: string;
  ragName?: string;
  name?: string;
  description?: string;
  scope?: 'GLOBAL' | 'PROJECT' | string;
  projectId?: string;
  status?: number | string;
  statusCode?: number;
  chunkCount: number;
  documentCount: number;
  sourceType?: string;
  usedProjectCount?: number;
  projectCount?: number;
  createTime?: string;
  updateTime?: string;
  retrievalPolicyJson?: string;
  ragOrders?: Array<Record<string, any>>;
}

export interface RagKnowledgeBaseUsageProject {
  projectId: string;
  projectName?: string;
  status?: string;
  enabledBy?: string;
  enabledTime?: string;
  updateTime?: string;
}

export interface RagKnowledgeDocumentRecord {
  documentId?: string;
  chunkId?: string;
  kbId?: string;
  knowledgeTag?: string;
  tag?: string;
  fileName?: string;
  displayName?: string;
  source?: string;
  sourceType?: string;
  documentType?: string;
  fileType?: string;
  chunkIndex?: number;
  chunkStrategy?: string;
  size?: number;
  content?: string;
  parseStatus?: string;
  embeddingStatus?: string;
  structurePreserved?: boolean;
  previewable?: boolean;
  updateTime?: string;
}

export interface RagKnowledgeRetrievalPolicy {
  id?: number;
  kbId?: string;
  knowledgeTag?: string;
  scope?: 'GLOBAL' | 'PROJECT' | string;
  projectId?: string;
  segmentationMode?: 'STRUCTURE_FIRST' | string;
  structurePreserved?: boolean;
  maxSegmentChars?: number;
  hardSplitOverlapChars?: number;
  /**
   * Legacy aliases backed by the existing database columns. New UI should use
   * maxSegmentChars and hardSplitOverlapChars.
   */
  chunkSize?: number;
  overlapSize?: number;
  topK?: number;
  rerankEnabled?: boolean;
  embeddingModelId?: string;
  metadataFilterJson?: string;
  vectorStatus?: string;
  updateTime?: string;
}

export interface RagDocumentStats {
  tag?: string;
  chunkCount: number;
  documentCount: number;
  byType: Array<{ key: string; count: number }>;
  bySource: Array<{ key: string; count: number }>;
}

export interface RagQualityProbeRequest {
  query: string;
  knowledgeTag?: string;
  expectedKeywords?: string[];
  topK?: number;
}

export interface RagQualityProbeResult {
  query: string;
  knowledgeTag?: string;
  topK: number;
  hitCount: number;
  keywordCoverage: number;
  expectedKeywords: string[];
  coveredKeywords: string[];
  missingKeywords: string[];
  recommendation: string;
  hits: Array<Record<string, any>>;
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

export interface RagEvalRunResult {
  caseCount: number;
  hitRate: number;
  averageKeywordCoverage: number;
  mrr: number;
  passedCount: number;
  results: Array<Record<string, any>>;
}

export interface RagFeedbackRecord {
  id?: number;
  query?: string;
  queryText?: string;
  answer?: string;
  answerText?: string;
  useful?: boolean | number;
  resolved?: boolean | number;
  sourceType?: string;
  sourceId?: string;
  knowledgeTag?: string;
  chunkIds?: string[];
  chunkIdsJson?: string;
  comment?: string;
  commentText?: string;
  createTime?: string;
}

export interface RagKnowledgeGap {
  id: number;
  gapKey?: string;
  queryText: string;
  knowledgeTag?: string;
  status: string;
  feedbackCount?: number;
  sampleComment?: string;
  lastFeedbackAt?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export class AiClientRagOrderAdminService {
  private baseUrl: string;

  constructor() {
    this.baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ai-client-rag-order`;
  }

  /**
   * 创建知识库配置
   */
  async createRagOrder(request: AiClientRagOrderRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/create`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据ID更新知识库配置
   */
  async updateRagOrderById(request: AiClientRagOrderRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/update-by-id`, {
      method: 'PUT',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据知识库ID更新知识库配置
   */
  async updateRagOrderByRagId(request: AiClientRagOrderRequestDTO): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/update-by-rag-id`, {
      method: 'PUT',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 旧 RAG 配置兼容接口：按数据库 ID 移除记录。
   */
  async deleteRagOrderById(id: number): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/delete-by-id/${id}`, {
      method: 'DELETE',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 旧 RAG 配置兼容接口：按 ragId 移除记录。
   */
  async deleteRagOrderByRagId(ragId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/delete-by-rag-id/${ragId}`, {
      method: 'DELETE',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据ID查询知识库配置
   */
  async queryRagOrderById(id: number): Promise<ApiResponse<AiClientRagOrderResponseDTO>> {
    const response = await fetch(`${this.baseUrl}/query-by-id/${id}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据知识库ID查询知识库配置
   */
  async queryRagOrderByRagId(ragId: string): Promise<ApiResponse<AiClientRagOrderResponseDTO>> {
    const response = await fetch(`${this.baseUrl}/query-by-rag-id/${ragId}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 查询启用的知识库配置
   */
  async queryEnabledRagOrders(): Promise<ApiResponse<AiClientRagOrderResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-enabled`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据知识标签查询知识库配置
   */
  async queryRagOrdersByKnowledgeTag(knowledgeTag: string): Promise<ApiResponse<AiClientRagOrderResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-by-knowledge-tag/${knowledgeTag}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 根据状态查询知识库配置
   */
  async queryRagOrdersByStatus(status: number): Promise<ApiResponse<AiClientRagOrderResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-by-status/${status}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 分页查询知识库配置列表
   */
  async queryRagOrderList(request: AiClientRagOrderQueryRequestDTO): Promise<ApiResponse<AiClientRagOrderResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-list`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 查询所有知识库配置
   */
  async queryAllRagOrders(): Promise<ApiResponse<AiClientRagOrderResponseDTO[]>> {
    const response = await fetch(`${this.baseUrl}/query-all`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 上传知识库文件
   */
  async uploadRagFile(name: string, tag: string, files: File[]): Promise<ApiResponse<boolean>> {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('tag', tag);
    files.forEach(file => {
      formData.append('files', file);
    });

    // 获取token用于认证
    const token = localStorage.getItem('token');
    const headers: Record<string, string> = {};

    // 添加认证头（如果有token）
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(`${this.baseUrl}/file/upload`, {
      method: 'POST',
      headers: headers, // 不设置Content-Type，让浏览器自动设置multipart/form-data
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 异步上传知识库文件。当前仅支持 Markdown / PDF，避免前端等待解析和 embedding。
   */
  async uploadRagFileAsync(
    name: string,
    tag: string,
    files: File[]
  ): Promise<ApiResponse<RagIngestionJobRecord>> {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('tag', tag);
    files.forEach((file) => {
      formData.append('files', file);
    });

    const token = localStorage.getItem('token');
    const headers: Record<string, string> = {};
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${this.baseUrl}/file/upload-async`, {
      method: 'POST',
      headers,
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async queryIngestionJob(jobId: string): Promise<ApiResponse<RagIngestionJobRecord>> {
    const response = await fetch(`${this.baseUrl}/file/jobs/${encodeURIComponent(jobId)}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listIngestionJobs(limit = 20): Promise<ApiResponse<RagIngestionJobRecord[]>> {
    const response = await fetch(`${this.baseUrl}/file/jobs?limit=${encodeURIComponent(limit)}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listKnowledgeBases(): Promise<ApiResponse<RagKnowledgeBaseSummary[]>> {
    const response = await fetch(`${this.baseUrl}/knowledge-bases`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listGlobalKnowledgeBases(): Promise<ApiResponse<RagKnowledgeBaseSummary[]>> {
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async getGlobalKnowledgeStats(kbId?: string): Promise<ApiResponse<RagDocumentStats>> {
    const query = kbId ? `?kbId=${encodeURIComponent(kbId)}` : '';
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global/stats${query}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async createGlobalKnowledgeBase(request: Partial<RagKnowledgeBaseSummary>): Promise<ApiResponse<RagKnowledgeBaseSummary>> {
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateGlobalKnowledgeBase(
    kbId: string,
    request: Partial<RagKnowledgeBaseSummary>
  ): Promise<ApiResponse<RagKnowledgeBaseSummary>> {
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}`, {
      method: 'PUT',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateGlobalKnowledgeBaseStatus(
    kbId: string,
    status: 'ENABLED' | 'DISABLED' | string
  ): Promise<ApiResponse<RagKnowledgeBaseSummary>> {
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}/status`, {
      method: 'PATCH',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify({ status }),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async uploadGlobalKnowledgeDocuments(
    kbId: string,
    name: string,
    files: File[]
  ): Promise<ApiResponse<RagIngestionJobRecord>> {
    const formData = new FormData();
    formData.append('name', name);
    files.forEach((file) => formData.append('files', file));

    const token = localStorage.getItem('token');
    const headers: Record<string, string> = {};
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}/documents`, {
      method: 'POST',
      headers,
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listGlobalKnowledgeDocuments(kbId: string): Promise<ApiResponse<RagKnowledgeDocumentRecord[]>> {
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}/documents`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listGlobalKnowledgeBaseUsageProjects(
    kbId: string
  ): Promise<ApiResponse<RagKnowledgeBaseUsageProject[]>> {
    const response = await fetch(
      `${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}/usage-projects`,
      {
        method: 'GET',
        headers: {
          ...DEFAULT_HEADERS,
        },
      },
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listGlobalKnowledgeChunks(kbId: string, limit = 100): Promise<ApiResponse<RagKnowledgeDocumentRecord[]>> {
    const normalizedKbId = kbId.trim();
    if (!normalizedKbId) {
      throw new Error("KNOWLEDGE_BASE_ID_REQUIRED");
    }
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(normalizedKbId)}/chunks?limit=${encodeURIComponent(limit)}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteGlobalKnowledgeChunk(kbId: string, chunkId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(
      `${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}/chunks/${encodeURIComponent(chunkId)}`,
      {
        method: 'DELETE',
        headers: {
          ...DEFAULT_HEADERS,
        },
      },
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteGlobalKnowledgeChunks(kbId: string): Promise<ApiResponse<Record<string, any>>> {
    const response = await fetch(
      `${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}/chunks`,
      {
        method: 'DELETE',
        headers: {
          ...DEFAULT_HEADERS,
        },
      },
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async getGlobalRetrievalPolicy(kbId: string): Promise<ApiResponse<RagKnowledgeRetrievalPolicy>> {
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}/retrieval-policy`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateGlobalRetrievalPolicy(
    kbId: string,
    request: Partial<RagKnowledgeRetrievalPolicy>
  ): Promise<ApiResponse<RagKnowledgeRetrievalPolicy>> {
    const response = await fetch(`${this.baseUrl}/knowledge-bases/global/${encodeURIComponent(kbId)}/retrieval-policy`, {
      method: 'PUT',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listProjectKnowledgeBases(projectId: string): Promise<ApiResponse<RagKnowledgeBaseSummary[]>> {
    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async createProjectKnowledgeBase(
    projectId: string,
    request: Partial<RagKnowledgeBaseSummary>,
  ): Promise<ApiResponse<RagKnowledgeBaseSummary>> {
    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async updateProjectKnowledgeBase(
    projectId: string,
    kbId: string,
    request: Partial<RagKnowledgeBaseSummary>,
  ): Promise<ApiResponse<RagKnowledgeBaseSummary>> {
    const response = await fetch(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}`,
      {
        method: 'PUT',
        headers: {
          ...DEFAULT_HEADERS,
        },
        body: JSON.stringify(request),
      },
    );
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async updateProjectKnowledgeBaseStatus(
    projectId: string,
    kbId: string,
    status: 'ENABLED' | 'DISABLED' | string,
  ): Promise<ApiResponse<RagKnowledgeBaseSummary>> {
    const response = await fetch(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}/status`,
      {
        method: 'PATCH',
        headers: {
          ...DEFAULT_HEADERS,
        },
        body: JSON.stringify({ status }),
      },
    );
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  }

  async getProjectKnowledgeStats(projectId: string, kbId?: string): Promise<ApiResponse<RagDocumentStats>> {
    const query = kbId ? `?kbId=${encodeURIComponent(kbId)}` : '';
    const response = await fetch(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/stats${query}`,
      {
        method: 'GET',
        headers: {
          ...DEFAULT_HEADERS,
        },
      },
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listAuthorizedKnowledgeBases(projectId: string): Promise<ApiResponse<RagKnowledgeBaseSummary[]>> {
    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/authorized-knowledge-bases`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async enableGlobalKnowledgeBaseForProject(
    projectId: string,
    globalKbId: string
  ): Promise<ApiResponse<RagKnowledgeBaseSummary>> {
    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/enable-global`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify({ globalKbId }),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async uploadProjectKnowledgeDocuments(
    projectId: string,
    kbId: string,
    name: string,
    files: File[]
  ): Promise<ApiResponse<RagIngestionJobRecord>> {
    const formData = new FormData();
    formData.append('name', name);
    files.forEach((file) => formData.append('files', file));

    const token = localStorage.getItem('token');
    const headers: Record<string, string> = {};
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}/documents`, {
      method: 'POST',
      headers,
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listProjectKnowledgeDocuments(
    projectId: string,
    kbId: string
  ): Promise<ApiResponse<RagKnowledgeDocumentRecord[]>> {
    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}/documents`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listProjectKnowledgeChunks(
    projectId: string,
    kbId: string,
    limit = 100
  ): Promise<ApiResponse<RagKnowledgeDocumentRecord[]>> {
    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}/chunks?limit=${encodeURIComponent(limit)}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteProjectKnowledgeChunk(
    projectId: string,
    kbId: string,
    chunkId: string
  ): Promise<ApiResponse<boolean>> {
    const response = await fetch(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}/chunks/${encodeURIComponent(chunkId)}`,
      {
        method: 'DELETE',
        headers: {
          ...DEFAULT_HEADERS,
        },
      },
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteProjectKnowledgeChunks(
    projectId: string,
    kbId: string
  ): Promise<ApiResponse<Record<string, any>>> {
    const response = await fetch(
      `${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}/chunks`,
      {
        method: 'DELETE',
        headers: {
          ...DEFAULT_HEADERS,
        },
      },
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async getProjectRetrievalPolicy(
    projectId: string,
    kbId: string
  ): Promise<ApiResponse<RagKnowledgeRetrievalPolicy>> {
    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}/retrieval-policy`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateProjectRetrievalPolicy(
    projectId: string,
    kbId: string,
    request: Partial<RagKnowledgeRetrievalPolicy>
  ): Promise<ApiResponse<RagKnowledgeRetrievalPolicy>> {
    const response = await fetch(`${this.baseUrl}/projects/${encodeURIComponent(projectId)}/knowledge-bases/${encodeURIComponent(kbId)}/retrieval-policy`, {
      method: 'PUT',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async queryDocumentStats(tag?: string): Promise<ApiResponse<RagDocumentStats>> {
    const query = tag ? `?tag=${encodeURIComponent(tag)}` : '';
    const response = await fetch(`${this.baseUrl}/document/stats${query}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async probeRagQuality(request: RagQualityProbeRequest): Promise<ApiResponse<RagQualityProbeResult>> {
    const response = await fetch(`${this.baseUrl}/quality/probe`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listRagEvalCases(enabled?: boolean): Promise<ApiResponse<RagEvalCase[]>> {
    const query = enabled === undefined ? '' : `?enabled=${encodeURIComponent(String(enabled))}`;
    const response = await fetch(`${this.baseUrl}/quality/eval-cases${query}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async saveRagEvalCase(request: RagEvalCase): Promise<ApiResponse<RagEvalCase>> {
    const response = await fetch(`${this.baseUrl}/quality/eval-cases`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteRagEvalCase(id: number): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/quality/eval-cases/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async runRagEval(cases?: RagEvalCase[]): Promise<ApiResponse<RagEvalRunResult>> {
    const response = await fetch(`${this.baseUrl}/quality/eval-run`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(cases ? { cases } : {}),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async submitRagFeedback(request: RagFeedbackRecord): Promise<ApiResponse<Record<string, any>>> {
    const response = await fetch(`${this.baseUrl}/quality/feedback`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listRagFeedback(params: {
    knowledgeTag?: string;
    useful?: boolean;
    resolved?: boolean;
    limit?: number;
  } = {}): Promise<ApiResponse<RagFeedbackRecord[]>> {
    const query = new URLSearchParams();
    if (params.knowledgeTag) query.set('knowledgeTag', params.knowledgeTag);
    if (params.useful !== undefined) query.set('useful', String(params.useful));
    if (params.resolved !== undefined) query.set('resolved', String(params.resolved));
    query.set('limit', String(params.limit || 100));
    const response = await fetch(`${this.baseUrl}/quality/feedback?${query.toString()}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async listRagKnowledgeGaps(params: {
    status?: string;
    knowledgeTag?: string;
    limit?: number;
  } = {}): Promise<ApiResponse<RagKnowledgeGap[]>> {
    const query = new URLSearchParams();
    if (params.status) query.set('status', params.status);
    if (params.knowledgeTag) query.set('knowledgeTag', params.knowledgeTag);
    query.set('limit', String(params.limit || 100));
    const response = await fetch(`${this.baseUrl}/quality/knowledge-gaps?${query.toString()}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async updateRagKnowledgeGapStatus(id: number, status: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/quality/knowledge-gaps/${encodeURIComponent(id)}/status?status=${encodeURIComponent(status)}`, {
      method: 'PUT',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async saveRagKnowledgeGapAsEvalCase(id: number): Promise<ApiResponse<RagEvalCase>> {
    const response = await fetch(`${this.baseUrl}/quality/knowledge-gaps/${encodeURIComponent(id)}/eval-case`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteRagChunk(chunkId: string): Promise<ApiResponse<boolean>> {
    const response = await fetch(`${this.baseUrl}/document/chunks/${encodeURIComponent(chunkId)}`, {
      method: 'DELETE',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  async deleteRagChunksByTag(tag: string): Promise<ApiResponse<Record<string, any>>> {
    const response = await fetch(`${this.baseUrl}/document/by-tag/${encodeURIComponent(tag)}`, {
      method: 'DELETE',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 查询可预览文档列表
   */
  async queryRagDocuments(tag?: string): Promise<ApiResponse<RagDocumentResponseDTO[]>> {
    const query = tag ? `?tag=${encodeURIComponent(tag)}` : '';
    const response = await fetch(`${this.baseUrl}/document/list${query}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }

  /**
   * 查询文档内容
   */
  async queryRagDocumentContent(fileName: string): Promise<ApiResponse<RagDocumentResponseDTO>> {
    const response = await fetch(`${this.baseUrl}/document/content?fileName=${encodeURIComponent(fileName)}`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return await response.json();
  }
}

export const aiClientRagOrderAdminService = new AiClientRagOrderAdminService();
