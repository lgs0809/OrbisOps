import { describe, expect, it } from 'vitest';

import { appendLiveRunStep, toLiveRunStep, userFacingRunFailure } from './live-run-trace';

describe('live run trace', () => {
  it('projects runtime facts without exposing raw tool inputs or outputs', () => {
    const step = toLiveRunStep({
      eventType: 'TOOL_CALL_STARTED',
      status: 'RUNNING',
      payload: {
        toolName: 'project_mcp_sample-project_prometheus_query',
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

  it('turns provider reason codes into safe actionable chat copy', () => {
    const message = userFacingRunFailure({
      eventType: 'REACT_FAILED',
      status: 'FAILED',
      summary: 'raw provider response must not be shown',
      payload: { reasonCode: 'MODEL_PROVIDER_AUTH_FAILED' },
    });

    expect(message).toContain('鉴权失败');
    expect(message).toContain('未执行任何生产变更');
    expect(message).not.toContain('raw provider');
  });

  it('keeps unknown run failures generic', () => {
    expect(userFacingRunFailure({
      eventType: 'RUN_FAILED',
      status: 'FAILED',
      summary: 'sensitive internal details',
    })).toBe('本轮执行未完成。已保留运行过程和获得的观测，请检查模型或项目连接状态后重试。');
  });
});
