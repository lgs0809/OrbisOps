import type { OpsRuntimeEvent } from '../../services/ops-admin-service';

export interface LiveRunStep {
  key: string;
  label: string;
  status: 'running' | 'success' | 'warning' | 'error';
  timestamp?: string;
}

const text = (value: unknown) => String(value ?? '').trim();

const short = (value: string, limit = 80) => (
  value.length > limit ? `${value.slice(0, Math.max(0, limit - 1))}…` : value
);

const OUTCOME_KEYS = new Set([
  'requiresAction',
  'verificationStatus',
  'abstained',
  'evidenceCompleteness',
]);

/** Removes protocol markers and a complete machine outcome from persisted or streamed chat text. */
export const sanitizeVisibleAnswer = (value: unknown): string => {
  let output = String(value ?? '');
  if (!output) return '';

  output = output
    .replace(/<ops_answer>/g, '')
    .replace(/<\/ops_answer>/g, '')
    // Some compatible providers echo an internal completion sentinel even
    // though it is not part of the public answer protocol.
    .replace(/<\/?CPA_DONE\s*\/?>/gi, '');
  const outcomeStart = output.lastIndexOf('<ops_outcome>');
  if (outcomeStart >= 0) output = output.slice(0, outcomeStart);

  const lines = output.split(/\r?\n/);
  let cursor = lines.length;
  while (cursor > 0) {
    const line = lines[cursor - 1].trim();
    if (line === '' || line === '</ops_outcome>') cursor -= 1;
    else break;
  }

  const seen = new Set<string>();
  let blockStart = cursor;
  for (let index = cursor - 1; index >= 0; index -= 1) {
    const line = lines[index].trim();
    if (line === '' || line === '</ops_outcome>') continue;
    const separator = line.indexOf('=');
    if (separator <= 0) break;
    const key = line.slice(0, separator).trim();
    if (!OUTCOME_KEYS.has(key) || seen.has(key)) break;
    seen.add(key);
    blockStart = index;
  }
  if (seen.size === OUTCOME_KEYS.size) {
    output = lines.slice(0, blockStart).join('\n');
  }

  return output.replace(/<ops_outcome>/g, '').replace(/<\/ops_outcome>/g, '').trim();
};

const toolLabel = (event: OpsRuntimeEvent) => {
  const payload = event.payload || {};
  const remoteTool = text(payload.remoteToolName);
  const toolName = remoteTool || text(payload.toolName);
  if (!toolName) return '项目工具';
  if (toolName === 'PrepareChangePackage') return 'ChangePackage';
  if (toolName === 'knowledge_retrieve') return '知识库';
  if (toolName === 'Skill' || toolName === 'UseProjectSkill') return 'Skill';
  if (toolName.startsWith('mcp_tool_catalog_')) return '项目工具目录';
  if (toolName.startsWith('enable_mcp_tool_')) return '工具使用说明';
  return short(toolName.replace(/^project_mcp_/, '').replace(/^enable_mcp_tool_/, ''), 52);
};

const stepStatus = (event: OpsRuntimeEvent): LiveRunStep['status'] => {
  const value = text(event.status).toUpperCase();
  if (value === 'FAILED') return 'error';
  if (['BLOCKED', 'DEGRADED', 'SKIPPED', 'PROPOSED', 'CANCELED'].includes(value)) return 'warning';
  if (['SUCCEEDED', 'READY', 'COMPLETED'].includes(value)) return 'success';
  return 'running';
};

/**
 * Projects durable runtime facts into a user-facing execution trace.
 * Raw prompts, model reasoning, tool arguments/results and internal schemas are intentionally excluded.
 */
export const toLiveRunStep = (event: OpsRuntimeEvent): LiveRunStep | null => {
  const type = text(event.eventType).toUpperCase();
  let label = '';

  switch (type) {
    case 'RUN_ACCEPTED':
    case 'RUN_STARTED':
      label = '开始处理请求';
      break;
    case 'MEMORY_CONTEXT_STARTED':
      label = '读取会话上下文';
      break;
    case 'MEMORY_CONTEXT_FINISHED':
      label = '会话上下文已准备';
      break;
    case 'QUERY_REWRITE_STARTED':
      label = '结合上下文理解当前问题';
      break;
    case 'QUERY_REWRITE_FINISHED':
      label = '已完成上下文指代解析';
      break;
    case 'QUERY_REWRITE_DEGRADED':
      label = '上下文改写不可用，继续使用原问题';
      break;
    case 'RESOURCE_ASSEMBLY_STARTED':
    case 'RUNTIME_CONTEXT_BUNDLE_CREATED':
      label = '加载当前项目的上下文与能力';
      break;
    case 'RESOURCE_ASSEMBLY_FINISHED':
    case 'REACT_AGENT_READY':
      label = '当前项目能力已就绪';
      break;
    case 'REACT_STARTED':
      label = '开始分析并选择下一步';
      break;
    case 'MODEL_CALL_QUEUED':
      label = '等待模型分析';
      break;
    case 'MODEL_CALL_STARTED':
      label = '正在分析下一步';
      break;
    case 'MODEL_CALL_RETRYING': {
      const next = Number(event.payload?.nextAttempt);
      const max = Number(event.payload?.maxAttempts);
      const counter = Number.isInteger(next) && Number.isInteger(max)
        && next > 1 && next <= max && max <= 10 ? `（${next}/${max}）` : '';
      const reason = event.payload?.reasonCode === 'MODEL_PROVIDER_RATE_LIMITED'
        ? '模型服务暂时限流' : '模型连接暂时异常';
      label = `${reason}，正在自动重试${counter}`;
      break;
    }
    case 'MODEL_CALL_FINISHED':
      label = '本轮分析完成';
      break;
    case 'MODEL_CALL_FAILED':
      label = '本轮模型分析失败';
      break;
    case 'TOOL_CALL_STARTED':
      label = `正在调用 ${toolLabel(event)}`;
      break;
    case 'TOOL_CALL_FINISHED':
      label = `${toolLabel(event)} 已返回结果`;
      break;
    case 'TOOL_CALL_PROPOSED':
      label = `${toolLabel(event)} 需要进入 ChangePackage 才能执行`;
      break;
    case 'TOOL_CALL_BLOCKED':
      label = `${toolLabel(event)} 未执行：当前运行边界不允许`;
      break;
    case 'TOOL_CALL_FAILED':
      label = `${toolLabel(event)} 调用失败`;
      break;
    case 'SOURCE_QUERY_STARTED':
      label = '正在查询实时数据源';
      break;
    case 'SOURCE_QUERY_FINISHED':
      label = '实时数据源已返回观测';
      break;
    case 'RAG_RETRIEVE':
      label = text(event.status).toUpperCase() === 'NOT_FOUND' ? '知识库未找到相关材料' : '已检索项目知识';
      break;
    case 'SKILL_CONTEXT_LOADED':
      label = '已加载当前任务需要的 Skill';
      break;
    case 'CHANGE_PACKAGE_EVIDENCE_COMPLETION_STARTED':
      label = '正在补全 ChangePackage 所需证据';
      break;
    case 'CHANGE_PACKAGE_PREPARED':
      label = '已创建 ChangePackage，等待审核';
      break;
    case 'CHANGE_PACKAGE_SKIPPED':
      label = '当前条件不足，未创建 ChangePackage';
      break;
    case 'CHANGE_PACKAGE_EVALUATION_FAILED':
      label = 'ChangePackage 准备失败，未执行生产变更';
      break;
    case 'WORKFLOW_APPROVAL_WAITING':
      label = '等待人工审批';
      break;
    case 'REACT_TRANSPORT_RETRY':
      label = '模型连接异常，正在安全重试';
      break;
    case 'REACT_FINISHED':
      label = 'ReAct 执行完成，正在整理回答';
      break;
    case 'REACT_FAILED':
      label = 'ReAct 执行未完成';
      break;
    case 'RUN_CANCELED':
      label = '运行已取消';
      break;
    case 'RUN_FAILED':
      label = '运行失败';
      break;
    default:
      return null;
  }

  return {
    key: `${type}:${text(event.nodeId)}:${text(event.timestamp)}:${label}`,
    label,
    status: stepStatus(event),
    timestamp: event.timestamp,
  };
};

export const appendLiveRunStep = (steps: LiveRunStep[], event: OpsRuntimeEvent, limit = 12) => {
  const next = toLiveRunStep(event);
  if (!next) return steps;
  // SSE replay and nested callbacks can deliver an event more than once.
  // Duplicate React keys leave stale DOM rows even though this list is bounded.
  if (steps.some((step) => step.key === next.key)) return steps;
  const last = steps[steps.length - 1];
  if (last && last.label === next.label && last.status === next.status) {
    return [...steps.slice(0, -1), next];
  }
  return [...steps, next].slice(-limit);
};

/** Maps stable runtime reason codes to safe, actionable chat copy. */
export const userFacingRunFailure = (event: OpsRuntimeEvent) => {
  const payload = event.payload || {};
  const diagnostic = [payload.reasonCode, event.summary, event.content]
    .map(text)
    .join(' ')
    .toUpperCase();

  if (diagnostic.includes('MODEL_PROVIDER_QUOTA_EXHAUSTED')) {
    return '模型供应方额度已用尽，本轮未完成。请检查额度或切换已授权模型后重试；已有工具回执和运行记录已保留。';
  }
  if (diagnostic.includes('MODEL_PROVIDER_AUTH_FAILED')) {
    return '模型供应方鉴权失败，本轮未完成。请检查当前模型的凭据配置后重试；已有工具回执和运行记录已保留。';
  }
  if (diagnostic.includes('MODEL_PROVIDER_RATE_LIMITED')) {
    return '模型供应方当前限流，本轮未完成。请稍后重试或切换已授权模型；已有工具回执和运行记录已保留。';
  }
  if (diagnostic.includes('MODEL_PROVIDER_UNAVAILABLE')) {
    return '模型供应方暂时不可用，本轮未完成。请在服务恢复后重试，并核对已有工具回执与实际资源状态。';
  }
  return '本轮执行未完成。已保留运行过程和获得的观测，请检查模型或项目连接状态后重试。';
};

/** Transport completion alone does not establish a business outcome. */
export const answerAfterStreamClosed = (content: unknown) =>
  sanitizeVisibleAnswer(content).trim()
  || '连接已结束，但尚未收到最终结果。任务可能仍在后台执行，请打开“查看运行”核对状态和已有回执。';
