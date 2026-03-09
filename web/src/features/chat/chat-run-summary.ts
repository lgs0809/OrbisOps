import { OpsRuntimeEvent } from '../../services/ops-admin-service';

export type ChatRunStage = 'idle' | 'starting' | 'investigating' | 'evidence' | 'synthesizing' | 'action' | 'completed' | 'blocked';

export interface ChatRunSummary {
  stage: ChatRunStage;
  label: string;
  detail: string;
  evidenceCount: number;
  toolCallCount: number;
  terminal: boolean;
}

const normalized = (value?: string) => String(value || '').trim().toUpperCase();

const eventMatches = (event: OpsRuntimeEvent, token: string) => normalized(event.eventType).includes(token);

const isSuccess = (event: OpsRuntimeEvent) => ['SUCCEEDED', 'SUCCESS', 'FOUND'].includes(normalized(event.status));

export const summarizeChatRun = (
  events: OpsRuntimeEvent[],
  sending: boolean,
  changePackageCount = 0,
): ChatRunSummary => {
  const evidenceEvents = events.filter((event) => eventMatches(event, 'EVIDENCE') && isSuccess(event));
  const successfulToolCalls = events.filter((event) => eventMatches(event, 'TOOL_CALL_FINISHED') && isSuccess(event));
  const evidenceCount = Math.max(evidenceEvents.length, successfulToolCalls.length);
  const toolCallCount = successfulToolCalls.length;
  const latest = events[events.length - 1];

  const failed = [...events].reverse().find((event) => {
    const status = normalized(event.status);
    return ['FAILED', 'ERROR', 'BLOCKED'].includes(status)
      && !eventMatches(event, 'TOOL_CALL_FINISHED');
  });
  const terminal = [...events].reverse().find((event) => {
    const type = normalized(event.eventType);
    return type.includes('WORKFLOW_OUTCOME')
      || type.includes('REACT_OUTCOME')
      || type.includes('RUN_FINISHED')
      || type === 'DONE'
      || type.includes('FINAL_REPORT');
  });

  if (!sending && failed && (!terminal || normalized(terminal.status) !== 'SUCCEEDED')) {
    return {
      stage: 'blocked',
      label: '本次诊断已受阻',
      detail: failed.summary || failed.content || '运行未能形成可信结论，可展开运行详情查看失败点。',
      evidenceCount,
      toolCallCount,
      terminal: true,
    };
  }
  if (!sending && terminal && isSuccess(terminal)) {
    return {
      stage: changePackageCount > 0 ? 'action' : 'completed',
      label: changePackageCount > 0 ? '诊断完成，已有处置方案' : '诊断完成',
      detail: terminal.summary || terminal.content || (evidenceCount > 0 ? `已基于 ${evidenceCount} 条运行证据形成结论。` : '已形成本次运行结论。'),
      evidenceCount,
      toolCallCount,
      terminal: true,
    };
  }
  if (!sending && changePackageCount > 0) {
    return {
      stage: 'action',
      label: '已有待处理处置方案',
      detail: '处置方案需要在执行中心按当前项目权限继续审核或执行。',
      evidenceCount,
      toolCallCount,
      terminal: true,
    };
  }
  if (sending) {
    if (latest && (eventMatches(latest, 'FINAL') || eventMatches(latest, 'REPORT') || eventMatches(latest, 'OUTCOME'))) {
      return { stage: 'synthesizing', label: '正在形成诊断结论', detail: latest.summary || latest.content || '正在把已验证事实与未知项整理成可执行结论。', evidenceCount, toolCallCount, terminal: false };
    }
    if (evidenceCount > 0) {
      return { stage: 'evidence', label: '已获取真实证据，正在继续分析', detail: latest?.summary || latest?.content || `目前已完成 ${toolCallCount} 次可信工具查询。`, evidenceCount, toolCallCount, terminal: false };
    }
    if (latest && (eventMatches(latest, 'TOOL') || eventMatches(latest, 'SOURCE_QUERY') || eventMatches(latest, 'RAG'))) {
      return { stage: 'investigating', label: '正在查询真实系统', detail: latest.summary || latest.content || '正在读取指标、日志、数据或知识来源。', evidenceCount, toolCallCount, terminal: false };
    }
    return { stage: events.length ? 'investigating' : 'starting', label: events.length ? '正在分析问题' : '正在启动诊断', detail: latest?.summary || latest?.content || '主助手正在确定需要查询的证据与下一步。', evidenceCount, toolCallCount, terminal: false };
  }
  if (events.length > 0) {
    return { stage: evidenceCount > 0 ? 'completed' : 'idle', label: evidenceCount > 0 ? '最近一次运行已有可信证据' : '最近一次运行已结束', detail: latest?.summary || latest?.content || '可展开运行详情查看完整过程。', evidenceCount, toolCallCount, terminal: true };
  }
  return { stage: 'idle', label: '等待开始诊断', detail: '直接描述现象或目标，主助手会按需查询真实系统。', evidenceCount: 0, toolCallCount: 0, terminal: true };
};
