import { DEFAULT_HEADERS } from '../config/api';

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export class ApiRequestError extends Error {
  status: number;
  statusText: string;
  responseBody: string;

  constructor(status: number, statusText: string, responseBody: string) {
    const detail = responseBody ? `: ${responseBody}` : '';
    super(`HTTP ${status} ${statusText}${detail}`);
    this.name = 'ApiRequestError';
    this.status = status;
    this.statusText = statusText;
    this.responseBody = responseBody;
  }
}

export const assertOpsResponse = async (response: Response) => {
  if (response.ok) return;

  let responseBody = '';
  try {
    const text = await response.text();
    if (text) {
      try {
        const json = JSON.parse(text);
        responseBody = json.info || json.message || text;
      } catch {
        responseBody = text;
      }
    }
  } catch {
    responseBody = '';
  }
  throw new ApiRequestError(response.status, response.statusText, responseBody);
};

export const buildOpsHeaders = (): Record<string, string> => ({
  ...DEFAULT_HEADERS,
});

// Consumed locally by the fetch interceptor; never sent as an HTTP header.
export interface OpsRequestInit extends RequestInit { requestTimeoutMs?: number }

export const opsRequest = async <T>(url: string, init?: OpsRequestInit): Promise<ApiResponse<T>> => {
  const response = await fetch(url, {
    ...init,
    headers: {
      ...buildOpsHeaders(),
      ...(init?.headers || {}),
    },
  });
  await assertOpsResponse(response);
  return await response.json();
};
