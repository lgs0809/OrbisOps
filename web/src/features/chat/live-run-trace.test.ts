import { describe, expect, it } from 'vitest';

import {
  appendLiveRunStep,
  answerAfterStreamClosed,
  sanitizeVisibleAnswer,
  toLiveRunStep,
  userFacingRunFailure,
} from './live-run-trace';

describe('live run trace', () => {
  it('projects runtime facts without exposing raw tool inputs or outputs', () => {
    const step = toLiveRunStep({
      eventType: 'TOOL_CALL_STARTED',
      status: 'RUNNING',
      payload: {
        toolName: 'project_mcp_checkout_prometheus_query',
        input: '{"query":"raw-input"}',
        output: 'raw-result',
      },
    });

    expect(step?.label).toContain('prometheus_query');
    expect(step?.label).not.toContain('raw-input');
    expect(step?.label).not.toContain('raw-result');
  });

  it('shows ChangePackage preparation as a real runtime event', () => {
    expect(toLiveRunStep({
      eventType: 'CHANGE_PACKAGE_PREPARED',
      status: 'SUCCEEDED',
      payload: { packageId: 'cp-1' },
    })).toMatchObject({
      label: '已创建 ChangePackage，等待审核',
      status: 'success',
    });
  });

  it('ignores internal events that are not part of the user-facing execution trace', () => {
    expect(toLiveRunStep({
      eventType: 'RUNTIME_RESOURCES',
      status: 'SUCCEEDED',
      payload: { toolSchemas: ['internal'] },
    })).toBeNull();
  });

  it('deduplicates consecutive equivalent progress steps', () => {
    const first = appendLiveRunStep([], { eventType: 'RUN_ACCEPTED', status: 'RUNNING', timestamp: 't1' });
    const second = appendLiveRunStep(first, { eventType: 'RUN_STARTED', status: 'RUNNING', timestamp: 't2' });
    expect(second).toHaveLength(1);
    expect(second[0].timestamp).toBe('t2');
  });

  it('ignores interleaved replay without hiding a later distinct invocation', () => {
    const started = { eventType: 'TOOL_CALL_STARTED', status: 'RUNNING', timestamp: 't1',
      payload: { toolName: 'enable_mcp_tool_service' } };
    const finished = { ...started, eventType: 'TOOL_CALL_FINISHED', status: 'SUCCEEDED', timestamp: 't2' };
    let steps = appendLiveRunStep(appendLiveRunStep([], started), finished);
    for (let i = 0; i < 30; i += 1) {
      steps = appendLiveRunStep(appendLiveRunStep(steps, started), finished);
    }
    expect(steps).toHaveLength(2);
    expect(new Set(steps.map((step) => step.key)).size).toBe(2);
    expect(appendLiveRunStep(steps, { ...started, timestamp: 't3' })).toHaveLength(3);
  });

  it('turns provider reason codes into safe actionable chat copy', () => {
    const message = userFacingRunFailure({
      eventType: 'REACT_FAILED',
      status: 'FAILED',
      summary: 'raw provider response must not be shown',
      payload: { reasonCode: 'MODEL_PROVIDER_AUTH_FAILED' },
    });

    expect(message).toContain('鉴权失败');
    expect(message).toContain('已有工具回执和运行记录已保留');
    expect(message).not.toContain('未执行任何生产变更');
    expect(message).not.toContain('raw provider');
  });

  it('keeps model retry in progress and excludes raw provider details from the chat', () => {
    const retry = toLiveRunStep({
      eventType: 'MODEL_CALL_RETRYING', status: 'RUNNING',
      summary: 'private-provider-error',
      payload: { nextAttempt: 2, maxAttempts: 3, reasonCode: 'MODEL_PROVIDER_UNAVAILABLE' },
    });
    expect(retry).toMatchObject({ label: '模型连接暂时异常，正在自动重试（2/3）', status: 'running' });
    expect(retry?.label).not.toContain('private-provider-error');
    expect(toLiveRunStep({
      eventType: 'MODEL_CALL_RETRYING', status: 'RUNNING',
      payload: { nextAttempt: 'raw-private-input', maxAttempts: 3 },
    })?.label).toBe('模型连接暂时异常，正在自动重试');
    expect(toLiveRunStep({ eventType: 'MODEL_RESPONSE_VERIFIED', status: 'SUCCEEDED' })).toBeNull();
  });

  it('keeps unknown run failures generic', () => {
    expect(userFacingRunFailure({
      eventType: 'RUN_FAILED',
      status: 'FAILED',
      summary: 'sensitive internal details',
    })).toBe('本轮执行未完成。已保留运行过程和获得的观测，请检查模型或项目连接状态后重试。');
  });

  it('removes malformed machine outcome tails before rendering an answer', () => {
    const answer = sanitizeVisibleAnswer(
      '我是 OrbisOps 的智能运维助手。\n</ops_outcome>\nrequiresAction=false\n'
      + 'verificationStatus=NOT_APPLICABLE\nabstained=false\nevidenceCompleteness=NOT_APPLICABLE\n'
      + '</ops_outcome>',
    );

    expect(answer).toBe('我是 OrbisOps 的智能运维助手。');
    expect(answer).not.toContain('requiresAction');
    expect(answer).not.toContain('ops_outcome');
  });

  it('removes provider completion sentinels before rendering an answer', () => {
    expect(sanitizeVisibleAnswer('结论已整理。<CPA_DONE>')).toBe('结论已整理。');
    expect(sanitizeVisibleAnswer('结论已整理。</CPA_DONE>')).toBe('结论已整理。');
  });
});

describe('closed stream without final answer', () => {
  it('keeps an honest pending outcome when EOF arrives before a final answer', () => {
    expect(answerAfterStreamClosed('')).toContain('尚未收到最终结果');
    expect(answerAfterStreamClosed('<CPA_DONE>')).toContain('查看运行');
  });
  it('retains received answers and explicit failure messages', () => {
    expect(answerAfterStreamClosed('已完成测试，等待审批。')).toBe('已完成测试，等待审批。');
    expect(answerAfterStreamClosed('模型服务暂时不可用。')).toBe('模型服务暂时不可用。');
  });
});
