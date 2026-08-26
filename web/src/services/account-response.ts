import type { ApiResponse } from './admin-user-service';

/** Keep safe application errors even when a rejected request uses an HTTP error status. */
export const readAccountResponse = async <T>(response: Response, fallback: string): Promise<T> => {
  const result = await response.json().catch(() => null) as ApiResponse<T> | null;
  if (!response.ok || result?.code !== '0000') {
    throw new Error(result?.info || fallback);
  }
  return result.data;
};
