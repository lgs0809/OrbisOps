/**
 * MCP 工具 API 服务
 */

import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';

// 定义响应数据类型
export interface AiClientToolMcpResponseDTO {
  id: number;
  mcpId: string;
  mcpName: string;
  description?: string;
  status: number;
  createTime: string;
  updateTime: string;
}

// 定义API响应格式
export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

/**
 * MCP 工具 API 服务类
 */
export class AiClientToolMcpService {
  private static readonly BASE_URL = API_ENDPOINTS.AI_CLIENT_TOOL_MCP.BASE;

  /**
   * 查询所有 MCP 工具配置
   */
  static async queryAllAiClientToolMcps(): Promise<AiClientToolMcpResponseDTO[]> {
    try {
      const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_CLIENT_TOOL_MCP.QUERY_ALL}`, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result: ApiResponse<AiClientToolMcpResponseDTO[]> = await response.json();

      if (result.code === '0000') {
        return result.data || [];
      } else {
        throw new Error(result.info || '查询失败');
      }
    } catch (error) {
      console.error('查询 MCP 工具配置失败:', error);
      // 返回空数组作为降级处理
      return [];
    }
  }
}
