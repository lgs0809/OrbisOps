/**
 * 管理员用户API服务
 */

import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';
import { readAccountResponse } from './account-response';

// 定义登录请求数据类型
export interface AdminUserLoginRequestDTO {
  phone?: string;
  username?: string;
  password: string;
}

// 定义API响应格式
export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export interface AdminUserResponseDTO {
  id?: number;
  userId?: string;
  username: string;
  phone?: string | null;
  userRole?: 'admin' | 'user' | string;
  status?: number;
  passwordChangeRequired?: boolean;
  token?: string;
}

export interface AdminUserCreateRequestDTO {
  userId?: string;
  username: string;
  phone?: string;
  password: string;
  userRole?: 'admin' | 'user' | string;
  status?: number;
}

/**
 * 管理员用户API服务类
 */
export class AdminUserService {
  private static readonly BASE_URL = API_ENDPOINTS.ADMIN_USER.BASE;

  static async loginAdminUser(
    loginData: AdminUserLoginRequestDTO
  ): Promise<AdminUserResponseDTO | null> {
    const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.ADMIN_USER.LOGIN}`, {
      method: 'POST',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(loginData),
    });
    return await readAccountResponse<AdminUserResponseDTO | null>(response, '登录失败，请稍后重试。');
  }

  /**
   * 验证管理员用户登录
   * @param loginData 登录数据
   * @returns Promise<boolean> 登录是否成功
   */
  static async validateAdminUserLogin(loginData: AdminUserLoginRequestDTO): Promise<boolean> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.ADMIN_USER.VALIDATE_LOGIN}`, {
        method: 'POST',
        headers: DEFAULT_HEADERS,
        body: JSON.stringify(loginData),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<boolean> = await response.json();

      if (result.code === '0000') {
        return result.data || false;
      } else {
        console.error('登录验证失败:', result.info);
        return false;
      }
    } catch (error) {
      console.error('登录验证请求失败:', error);
      return false;
    }
  }

  static async queryEnabledUsers(): Promise<AdminUserResponseDTO[]> {
    const token = localStorage.getItem('token');
    const response = await fetch(`${this.BASE_URL}/query-enabled`, {
      method: 'GET',
      headers: {
        ...DEFAULT_HEADERS,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const result: ApiResponse<AdminUserResponseDTO[]> = await response.json();
    if (result.code !== '0000') {
      throw new Error(result.info || '查询平台账号失败');
    }
    return result.data || [];
  }

  static async createUser(payload: AdminUserCreateRequestDTO): Promise<boolean> {
    const token = localStorage.getItem('token');
    const response = await fetch(`${this.BASE_URL}/create`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const result: ApiResponse<boolean> = await response.json();
    if (result.code !== '0000') {
      throw new Error(result.info || '创建平台账号失败');
    }
    return Boolean(result.data);
  }

  static async logout(): Promise<void> {
    const token = localStorage.getItem('token');
    const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.ADMIN_USER.LOGOUT}`, {
      method: 'POST',
      headers: {
        ...DEFAULT_HEADERS,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    });
    if (!await readAccountResponse<boolean>(response, '注销未完成，请稍后重试。')) {
      throw new Error('注销未完成，请稍后重试。');
    }
  }
}
