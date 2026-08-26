import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';
import type { AdminUserCreateRequestDTO, AdminUserResponseDTO } from './admin-user-service';
import { readAccountResponse } from './account-response';

export interface PlatformUserUpdateRequest extends Omit<AdminUserCreateRequestDTO, 'password'> {
  id?: number;
  password?: string;
}

const baseUrl = API_ENDPOINTS.ADMIN_USER.BASE;

export class PlatformUserService {
  static async list(): Promise<AdminUserResponseDTO[]> {
    const response = await fetch(`${baseUrl}/query-all`, { method: 'GET', headers: DEFAULT_HEADERS });
    return await readAccountResponse<AdminUserResponseDTO[]>(response, '加载成员失败，请稍后重试。') || [];
  }

  static async create(payload: AdminUserCreateRequestDTO): Promise<void> {
    const response = await fetch(`${baseUrl}/create`, {
      method: 'POST',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(payload),
    });
    if (!await readAccountResponse<boolean>(response, '添加成员失败，请稍后重试。')) throw new Error('添加成员未完成，请刷新后检查。');
  }

  static async update(payload: PlatformUserUpdateRequest): Promise<void> {
    const response = await fetch(`${baseUrl}/update-by-id`, {
      method: 'PUT',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(payload),
    });
    if (!await readAccountResponse<boolean>(response, '保存成员信息失败，请稍后重试。')) throw new Error('成员信息未保存，请刷新后检查。');
  }

  static async remove(userId: string): Promise<void> {
    const response = await fetch(`${baseUrl}/delete-by-user-id/${encodeURIComponent(userId)}`, {
      method: 'DELETE',
      headers: DEFAULT_HEADERS,
    });
    if (!await readAccountResponse<boolean>(response, '删除成员失败，请稍后重试。')) throw new Error('成员未删除，请刷新后检查。');
  }
}
