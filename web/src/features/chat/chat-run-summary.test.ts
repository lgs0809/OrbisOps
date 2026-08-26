import { describe, expect, it } from 'vitest';

import { OpsRuntimeEvent } from '../../services/ops-admin-service';
import { summarizeChatRun } from './chat-run-summary';

const event = (eventType: string, status: string, summary = ''): OpsRuntimeEvent => ({
  eventType,
  status,
  summary,
});

describe('summarizeChatRun', () => {
  it('does not turn an individual HTTP attempt failure into a failed task after the retry recovered', () => {
    const recovered = [event('MODEL_REQUEST_FAILED', 'FAILED', '本次连接异常'),
      event('MODEL_CALL_RETRYING', 'RUNNING'), event('MODEL_RESPONSE_VERIFIED', 'SUCCEEDED'),
      event('WORKFLOW_APPROVAL_WAITING', 'WAITING')];
    expect(summarizeChatRun(recovered, false).stage).not.toBe('blocked');
    expect(summarizeChatRun([...recovered, event('MODEL_CALL_FAILED', 'FAILED', '本轮模型分析失败')], false).stage).toBe('blocked');
  });
  it('keeps raw runtime events behind a human-readable investigation stage', () => {
    const summary = summarizeChatRun([
      event('MODEL_CALL_STARTED', 'RUNNING'),
      event('TOOL_CALL_STARTED', 'RUNNING', '查询最近 15 分钟指标'),
    ], true);

    expect(summary.stage).toBe('investigating');
    expect(summary.label).toBe('正在查询真实系统');
    expect(summary.terminal).toBe(false);
  });

  it('surfaces trusted tool completion as evidence progress', () => {
    const summary = summarizeChatRun([
      event('TOOL_CALL_FINISHED', 'SUCCEEDED', 'Prometheus 查询完成'),
      event('SOURCE_QUERY_FINISHED', 'SUCCEEDED'),
    ], true);

    expect(summary.stage).toBe('evidence');
    expect(summary.evidenceCount).toBe(1);
    expect(summary.toolCallCount).toBe(1);
  });

  it('reports a completed outcome and hands remediation off to execution center', () => {
    const summary = summarizeChatRun([
      event('TOOL_CALL_FINISHED', 'SUCCEEDED'),
      event('WORKFLOW_OUTCOME', 'SUCCEEDED', '已定位故障并形成建议'),
    ], false, 1);

    expect(summary.stage).toBe('action');
    expect(summary.label).toContain('处置方案');
    expect(summary.terminal).toBe(true);
  });

  it('recognizes persisted DONE as a completed historical run', () => {
    const summary = summarizeChatRun([
      event('SOURCE_QUERY_FINISHED', 'SUCCEEDED', 'Prometheus 查询完成'),
      event('DONE', 'SUCCEEDED', 'Agent 任务执行完成。'),
    ], false);

    expect(summary.stage).toBe('completed');
    expect(summary.label).toBe('诊断完成');
    expect(summary.terminal).toBe(true);
  });

  it('fails closed when the run terminates on a non-tool runtime failure', () => {
    const summary = summarizeChatRun([
      event('MODEL_CALL_FINISHED', 'FAILED', '模型服务不可用'),
    ], false);

    expect(summary.stage).toBe('blocked');
    expect(summary.detail).toContain('模型服务不可用');
  });
});
