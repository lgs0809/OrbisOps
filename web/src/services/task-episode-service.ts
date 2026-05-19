import { API_CONFIG } from '../config/api';
import { opsRequest } from './ops-http-client';

export interface TaskEpisodeView {
  model: string;
  episodes: Array<{ episodeId: string; goal: string; revision: number; outcome: string; lastConsolidatedRevision: number }>;
  turns: Array<{ turnSeq: number; endSeq: number; sourceRunRef: string; episodeId: string; episodeRevision: number;
    status: string; attempts: number; contextFidelity: string; inputHash: string; lastError: string; blockedByPrevious: boolean }>;
  jobs: Array<{ id: number; episodeId: string; revision: number; status: string; artifactType: string; lastError: string }>;
  artifacts: Array<{ episodeId: string; revision: number; content: string; outcome: string; contentHash: string }>;
}

export async function getTaskEpisodes(projectId: string, sessionId: string) {
  const params = new URLSearchParams({ projectId, sessionId });
  const result = await opsRequest<TaskEpisodeView>(`${API_CONFIG.BASE_DOMAIN}/api/v1/user/ops/task-episodes?${params}`);
  if (result.code !== '0000') throw new Error(result.info || '读取任务分段失败');
  return result.data;
}

export interface TaskAcceptanceView {
  episodeId: string; goal: string; revision: number; outcome: string; pendingTurns: number;
  receipts: Array<{ result_id: string; output_hash: string; status: string; tool_name: string; run_id: string }>;
  history: Array<{ acceptance_id: string; episode_revision: number; outcome: string; record_json: string; record_hash: string }>;
}
export interface TaskAcceptanceInput {
  requestId: string; revision: number; goalReview: string;
  criteria: Array<{ resultId: string; outputHash: string; pointer: string; operator: string; expected: unknown }>;
}
export interface TaskAcceptanceDraft {
  status: 'READY' | 'INSUFFICIENT_EVIDENCE'; explanation: string;
  request?: TaskAcceptanceInput;
  checks?: Array<{label: string; operator: string; expected: string | number | boolean}>;
}
export async function draftTaskAcceptance(projectId: string, episodeId: string, input: {revision: number; instruction: string}) {
  // The provider has a four-minute total retry budget; allow its final response to arrive.
  const result = await opsRequest<TaskAcceptanceDraft>(`${API_CONFIG.BASE_DOMAIN}/api/v1/user/ops/task-acceptance/${encodeURIComponent(episodeId)}/draft?projectId=${encodeURIComponent(projectId)}`, {method:'POST',body:JSON.stringify(input),requestTimeoutMs:260000});
  if (result.code !== '0000') throw new Error(result.info || '暂时无法整理验收条件，请稍后重试');
  return result.data;
}
export async function getTaskAcceptance(projectId: string, episodeId: string) {
  const result = await opsRequest<TaskAcceptanceView>(`${API_CONFIG.BASE_DOMAIN}/api/v1/user/ops/task-acceptance/${encodeURIComponent(episodeId)}?projectId=${encodeURIComponent(projectId)}`);
  if (result.code !== '0000') throw new Error(result.info || '读取验收记录失败');
  return result.data;
}
export async function verifyTaskAcceptance(projectId: string, episodeId: string, input: TaskAcceptanceInput) {
  const result = await opsRequest<{ outcome: string; acceptanceId: string }>(`${API_CONFIG.BASE_DOMAIN}/api/v1/user/ops/task-acceptance/${encodeURIComponent(episodeId)}?projectId=${encodeURIComponent(projectId)}`, {method:'POST',body:JSON.stringify(input)});
  if (result.code !== '0000') throw new Error(result.info || '任务验收失败');
  return result.data;
}
