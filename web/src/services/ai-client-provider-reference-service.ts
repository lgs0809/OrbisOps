import { API_CONFIG, DEFAULT_HEADERS } from '../config/api';

export interface AiClientProviderReferenceRequest {
  id?: number;
  apiId: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  credentialEnvironmentVariable: string;
  completionsPath: string;
  embeddingsPath: string;
  status: number;
}

export interface AiClientProviderReferenceMetadata {
  apiId: string;
  credentialEnvironmentVariable: string;
  environmentReference: boolean;
  legacyStoredCredential: boolean;
}

interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

const baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/v1/admin/ai-client-provider-references`;

const parse = async <T,>(response: Response): Promise<T> => {
  if (!response.ok) throw new Error(`Provider 请求失败，HTTP 状态码：${response.status}`);
  const result: ApiResponse<T> = await response.json();
  if (result.code !== '0000') throw new Error(result.info || 'Provider 凭据引用请求失败');
  return result.data;
};

export const aiClientProviderReferenceService = {
  async create(payload: AiClientProviderReferenceRequest): Promise<boolean> {
    return parse<boolean>(await fetch(baseUrl, {
      method: 'POST',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(payload),
    }));
  },

  async update(payload: AiClientProviderReferenceRequest): Promise<boolean> {
    return parse<boolean>(await fetch(baseUrl, {
      method: 'PUT',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(payload),
    }));
  },

  async metadata(apiId: string): Promise<AiClientProviderReferenceMetadata> {
    return parse<AiClientProviderReferenceMetadata>(await fetch(`${baseUrl}/${encodeURIComponent(apiId)}`, {
      method: 'GET',
      headers: DEFAULT_HEADERS,
    }));
  },
};
