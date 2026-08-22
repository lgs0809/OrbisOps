import { API_CONFIG, DEFAULT_HEADERS } from '../config';
import type { AdminUserResponseDTO, ApiResponse } from './admin-user-service';

export interface FirstTimeSetupRequest {
  username: string;
  password: string;
}

const baseUrl = `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/setup`;

export class FirstTimeSetupService {
  static async isRequired(): Promise<boolean> {
    const response = await fetch(`${baseUrl}/status`, {
      method: 'GET',
      headers: DEFAULT_HEADERS,
    });
    if (!response.ok) {
      throw new Error('无法读取初始化状态，请稍后重试。');
    }
    const result: ApiResponse<boolean> = await response.json();
    if (result.code !== '0000') {
      throw new Error(result.info || '无法读取初始化状态，请稍后重试。');
    }
    return Boolean(result.data);
  }

  static async createFirstAdministrator(payload: FirstTimeSetupRequest): Promise<AdminUserResponseDTO> {
    const response = await fetch(baseUrl, {
      method: 'POST',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(payload),
    });
    const result = await response.json().catch(() => null) as ApiResponse<AdminUserResponseDTO> | null;
    if (!response.ok || result?.code !== '0000' || !result.data?.token) {
      throw new Error(result?.info || '初始化失败，请稍后重试。');
    }
    return result.data;
  }
}
