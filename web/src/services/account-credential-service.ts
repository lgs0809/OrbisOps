import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';
import { readAccountResponse } from './account-response';
import type { AdminUserResponseDTO } from './admin-user-service';

const headers = () => ({ ...DEFAULT_HEADERS, Authorization: `Bearer ${localStorage.getItem('token') || ''}` });
export const accountCredentialService = {
  async profile(): Promise<AdminUserResponseDTO> {
    const response = await fetch('/api/v1/user/account', { headers: headers() });
    return readAccountResponse(response, '读取账号状态失败，请重试。');
  },
  async change(currentPassword: string, newPassword: string): Promise<AdminUserResponseDTO> {
    const response = await fetch('/api/v1/user/account/password', {
      method: 'POST', headers: headers(), body: JSON.stringify({ currentPassword, newPassword }),
    });
    return readAccountResponse(response, '密码修改失败，请重试。');
  },
  async reset(userId: string): Promise<boolean> {
    const response = await fetch(`${API_ENDPOINTS.ADMIN_USER.BASE}/${encodeURIComponent(userId)}/reset-password`, {
      method: 'POST', headers: headers(),
    });
    const result = await readAccountResponse<boolean>(response, '初始密码重置失败，请重试。');
    if (result !== true) throw new Error('重置未完成，请刷新后重试。');
    return true;
  },
};
