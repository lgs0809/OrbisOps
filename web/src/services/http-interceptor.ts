import { API_CONFIG } from '../config/api';
import { clearAuthSession } from './auth-session';
import type { OpsRequestInit } from './ops-http-client';

let installed = false;
const TRACE_HEADER = 'X-Trace-Id';

const requestUrl = (input: RequestInfo | URL): string => {
  if (typeof input === 'string') {
    return input;
  }
  if (input instanceof URL) {
    return input.toString();
  }
  return input.url;
};

const isRequest = (input: RequestInfo | URL): input is Request =>
  typeof Request !== 'undefined' && input instanceof Request;

export const installHttpInterceptor = () => {
  if (installed || typeof window === 'undefined') {
    return;
  }
  installed = true;
  installFetchFallback();
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: OpsRequestInit) => {
    const controller = new AbortController();
    const isStream = acceptsEventStream(init?.headers);
    const { requestTimeoutMs, ...requestInit } = init || {};
    const timeout = isStream ? 0 : typeof requestTimeoutMs === 'number' && Number.isFinite(requestTimeoutMs)
      && requestTimeoutMs > 0 ? Math.min(requestTimeoutMs, 300000) : API_CONFIG.TIMEOUT;
    let timer: number | undefined;
    let abortListener: (() => void) | undefined;
    if (init?.signal) {
      abortListener = () => controller.abort(init.signal?.reason);
      if (init.signal.aborted) {
        controller.abort(init.signal.reason);
      } else {
        init.signal.addEventListener('abort', abortListener, { once: true });
      }
    }
    if (timeout > 0) {
      timer = window.setTimeout(() => controller.abort(new DOMException('请求超时', 'TimeoutError')), timeout);
    }
    let response: Response;
    const headers = withTraceHeader(input, init?.headers);
    try {
      response = await nativeFetch(input, {
        ...requestInit,
        headers,
        signal: controller.signal,
      });
    } catch (error) {
      if ((error as Error)?.name === 'AbortError' || (error as Error)?.name === 'TimeoutError') {
        throw new Error('请求超时，请检查网络连接或服务状态。');
      }
      throw error;
    } finally {
      if (timer) {
        window.clearTimeout(timer);
      }
      if (init?.signal && abortListener) {
        init.signal.removeEventListener('abort', abortListener);
      }
    }
    const url = requestUrl(input);
    if (response.status === 401 && !url.includes('/admin-user/login')) {
      clearAuthSession();
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login';
      }
    }
    return response;
  };
};

const installFetchFallback = () => {
  if (typeof window.fetch === 'function') {
    return;
  }
  window.fetch = ((input: RequestInfo | URL, init?: RequestInit) =>
    new Promise<Response>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      const method = init?.method || methodOf(input);
      xhr.open(method, requestUrl(input), true);
      if (init?.credentials === 'include') {
        xhr.withCredentials = true;
      }
      new Headers(isRequest(input) ? input.headers : undefined).forEach((value, key) => {
        xhr.setRequestHeader(key, value);
      });
      if (init?.headers) {
        new Headers(init.headers).forEach((value, key) => {
          xhr.setRequestHeader(key, value);
        });
      }
      const abort = () => {
        xhr.abort();
        reject(new DOMException('请求已取消', 'AbortError'));
      };
      if (init?.signal) {
        if (init.signal.aborted) {
          abort();
          return;
        }
        init.signal.addEventListener('abort', abort, { once: true });
      }
      xhr.onload = () => {
        init?.signal?.removeEventListener('abort', abort);
        resolve(xhrResponse(xhr));
      };
      xhr.onerror = () => {
        init?.signal?.removeEventListener('abort', abort);
        reject(new TypeError('网络请求失败，请检查网络连接。'));
      };
      xhr.onabort = () => {
        init?.signal?.removeEventListener('abort', abort);
        reject(new DOMException('请求已取消', 'AbortError'));
      };
      xhr.send((init?.body as XMLHttpRequestBodyInit | null | undefined) ?? null);
    })) as typeof fetch;
};

const methodOf = (input: RequestInfo | URL): string => {
  if (isRequest(input) && input.method) {
    return input.method;
  }
  return 'GET';
};

const xhrResponse = (xhr: XMLHttpRequest): Response => {
  const headers = new Headers();
  xhr.getAllResponseHeaders()
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .forEach((line) => {
      const separator = line.indexOf(':');
      if (separator > 0) {
        headers.append(line.slice(0, separator).trim(), line.slice(separator + 1).trim());
      }
    });
  const body = xhr.responseText || '';
  if (typeof Response !== 'undefined') {
    return new Response(body, {
      status: xhr.status,
      statusText: xhr.statusText,
      headers,
    });
  }
  return {
    ok: xhr.status >= 200 && xhr.status < 300,
    status: xhr.status,
    statusText: xhr.statusText,
    headers,
    url: xhr.responseURL,
    body: null,
    text: async () => body,
    json: async () => JSON.parse(body),
  } as Response;
};

const withTraceHeader = (input: RequestInfo | URL, initHeaders?: HeadersInit): Headers => {
  const headers = new Headers(isRequest(input) ? input.headers : undefined);
  if (initHeaders) {
    new Headers(initHeaders).forEach((value, key) => headers.set(key, value));
  }
  if (!headers.has(TRACE_HEADER)) {
    headers.set(TRACE_HEADER, createTraceId());
  }
  return headers;
};

const createTraceId = (): string => {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi?.randomUUID) {
    return cryptoApi.randomUUID().replace(/-/g, '');
  }
  const bytes = new Uint8Array(16);
  if (cryptoApi?.getRandomValues) {
    cryptoApi.getRandomValues(bytes);
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  }
  return `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`.slice(0, 32).padEnd(32, '0');
};

const acceptsEventStream = (headers?: HeadersInit): boolean => {
  if (!headers) {
    return false;
  }
  if (headers instanceof Headers) {
    return (headers.get('Accept') || '').includes('text/event-stream');
  }
  if (Array.isArray(headers)) {
    return headers.some(([key, value]) => key.toLowerCase() === 'accept' && value.includes('text/event-stream'));
  }
  return Object.entries(headers).some(([key, value]) => key.toLowerCase() === 'accept' && String(value).includes('text/event-stream'));
};

export const logoutLocal = () => {
  clearAuthSession();
};
