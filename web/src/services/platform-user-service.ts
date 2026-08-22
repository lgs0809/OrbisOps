import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';
import type { AdminUserCreateRequestDTO, AdminUserResponseDTO, ApiResponse } from './admin-user-service';

export interface PlatformUserUpdateRequest extends AdminUserCreateRequestDTO {
  id?: number;
}

const baseUrl = API_ENDPOINTS.ADMIN_USER.BASE;

const parse = async <T>(response: Response): Promise<ApiResponse<T>> => {
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  const result: ApiResponse<T> = await response.json();
  if (result.code !== '0000') {
    throw new Error(result.info || 'Platform user request failed');
  }
  return result;
};

export class PlatformUserService {
  static async list(): Promise<AdminUserResponseDTO[]> {
    const response = await fetch(`${baseUrl}/query-all`, { method: 'GET', headers: DEFAULT_HEADERS });
    return (await parse<AdminUserResponseDTO[]>(response)).data || [];
  }

  static async create(payload: AdminUserCreateRequestDTO): Promise<void> {
    const response = await fetch(`${baseUrl}/create`, {
      method: 'POST',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(payload),
    });
    await parse<boolean>(response);
  }

  static async update(payload: PlatformUserUpdateRequest): Promise<void> {
    const response = await fetch(`${baseUrl}/update-by-user-id`, {
      method: 'PUT',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(payload),
    });
    await parse<boolean>(response);
  }

  static async remove(userId: string): Promise<void> {
    const response = await fetch(`${baseUrl}/delete-by-user-id/${encodeURIComponent(userId)}`, {
      method: 'DELETE',
      headers: DEFAULT_HEADERS,
    });
    await parse<boolean>(response);
  }
}
