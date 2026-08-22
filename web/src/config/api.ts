/**
 * API 配置文件
 * 统一管理所有 API 的基础 URL 和相关配置
 */

// 环境变量配置。浏览器运行时没有 Node 的 process 全局，优先使用构建工具注入的 import.meta.env。
declare const __ORBISOPS_API_BASE_URL__: string | undefined;

const runtimeEnv = ((import.meta as any).env || (globalThis as any).process?.env || {}) as Record<
  string,
  string | undefined
>;
const nodeEnv = runtimeEnv.NODE_ENV || runtimeEnv.MODE || 'development';
const isDevelopment = nodeEnv === 'development';
const isProduction = nodeEnv === 'production';
const apiBaseDomain =
  (typeof __ORBISOPS_API_BASE_URL__ !== 'undefined' ? __ORBISOPS_API_BASE_URL__ : '') ||
  runtimeEnv.ORBISOPS_API_BASE_URL ||
  (isDevelopment ? 'http://127.0.0.1:8099' : '');

// 基础配置
export const API_CONFIG = {
  // 基础域名配置
  BASE_DOMAIN: apiBaseDomain,

  // API 版本
  API_VERSION: 'v1',

  // 超时配置
  TIMEOUT: 12000, // 普通管理请求 12 秒超时；SSE 流式请求不走这个超时

  // 重试配置
  RETRY_TIMES: 3,
} as const;

// API 端点配置
export const API_ENDPOINTS = {
  // MCP 工具相关接口
  AI_CLIENT_TOOL_MCP: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/ai-client-tool-mcp`,
    CREATE: '/create',
    UPDATE_BY_ID: '/update-by-id',
    UPDATE_BY_MCP_ID: '/update-by-mcp-id',
    DELETE_BY_ID: '/delete-by-id',
    DELETE_BY_MCP_ID: '/delete-by-mcp-id',
    QUERY_BY_ID: '/query-by-id',
    QUERY_BY_MCP_ID: '/query-by-mcp-id',
    QUERY_ALL: '/query-all',
    QUERY_BY_STATUS: '/query-by-status',
    QUERY_BY_TRANSPORT_TYPE: '/query-by-transport-type',
    QUERY_ENABLED: '/query-enabled',
    QUERY_LIST: '/query-list',
  },

  // 模型目录相关接口
  AI_CLIENT_MODEL: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/ai-client-model`,
    CREATE: '/create',
    UPDATE_BY_ID: '/update-by-id',
    UPDATE_BY_MODEL_ID: '/update-by-model-id',
    DELETE_BY_ID: '/delete-by-id',
    DELETE_BY_MODEL_ID: '/delete-by-model-id',
    QUERY_BY_ID: '/query-by-id',
    QUERY_BY_MODEL_ID: '/query-by-model-id',
    QUERY_BY_API_ID: '/query-by-api-id',
    QUERY_BY_MODEL_TYPE: '/query-by-model-type',
    QUERY_ENABLED: '/query-enabled',
    QUERY_LIST: '/query-list',
    QUERY_ALL: '/query-all',
  },

  // 管理员用户相关接口
  ADMIN_USER: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/admin-user`,
    LOGIN: '/login',
    LOGOUT: '/logout',
    VALIDATE_LOGIN: '/validate-login',
  },

  // 可以在这里添加其他模块的 API 端点
  // USER: {
  //   BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/user`,
  //   LOGIN: '/login',
  //   LOGOUT: '/logout',
  // },

  // WORKFLOW: {
  //   BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/workflow`,
  //   SAVE: '/save',
  //   EXECUTE: '/execute',
  // },
} as const;

const defaultHeadersBase: Record<string, string> = {
  'Content-Type': 'application/json',
  Accept: 'application/json',
};

const authToken = (): string => {
  if (typeof localStorage === 'undefined') {
    return '';
  }
  return localStorage.getItem('token') || '';
};

// 请求头配置。用 Proxy 保持旧代码 `headers: DEFAULT_HEADERS` 和 `{...DEFAULT_HEADERS}` 都能动态带上登录 JWT。
export const DEFAULT_HEADERS = new Proxy(defaultHeadersBase, {
  get(target, prop: string | symbol) {
    if (prop === 'Authorization') {
      const token = authToken();
      return token ? `Bearer ${token}` : undefined;
    }
    if (typeof prop === 'symbol') {
      return Reflect.get(target, prop);
    }
    return target[prop];
  },
  ownKeys(target) {
    const keys = Reflect.ownKeys(target);
    return authToken() ? [...keys, 'Authorization'] : keys;
  },
  getOwnPropertyDescriptor(target, prop: string) {
    if (prop === 'Authorization') {
      const token = authToken();
      if (!token) {
        return undefined;
      }
      return {
        enumerable: true,
        configurable: true,
        value: `Bearer ${token}`,
      };
    }
    return Object.getOwnPropertyDescriptor(target, prop);
  },
}) as Record<string, string>;

// 导出便捷方法
export const getApiUrl = (endpoint: string): string => `${API_CONFIG.BASE_DOMAIN}${endpoint}`;

// 环境检查工具
export const ENV_UTILS = {
  isDevelopment,
  isProduction,
  isTest: nodeEnv === 'test',
} as const;
