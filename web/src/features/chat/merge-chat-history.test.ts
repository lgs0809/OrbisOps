import { describe, expect, it } from 'vitest';
import type { OpsChatMessage } from '../../services/ops-admin-service';
import { latestChatRun, mergeChatHistory, recoverableChatRun } from './merge-chat-history';

const message = (messageId: string, role: string, content: string): OpsChatMessage =>
  ({ messageId, role, content, sessionId: 's' } as OpsChatMessage);
const current = [message('local-user-1', 'user', '检查订单服务'),
  message('local-assistant-1', 'assistant', '连接中断，运行可能仍在继续。')];

describe('eventually consistent chat history', () => {
  it('preserves the new local turn when its initial empty history arrives late', () => {
    expect(mergeChatHistory(current, [], 's', true)).toEqual(current);
  });
  it('keeps the failure notice until the durable assistant answer exists', () => {
    expect(mergeChatHistory(current, [message('u', 'user', '检查订单服务')], 's')).toEqual(current);
  });
  it('replaces local placeholders with the completed persisted turn without duplication', () => {
    const history = [message('u', 'user', '检查订单服务'), message('a', 'assistant', '检查已完成')];
    expect(mergeChatHistory(current, history, 's')).toEqual(history);
  });
  it('does not mistake an older answer to the same question for this turn', () => {
    const old = [message('old-u', 'user', '检查订单服务'), message('old-a', 'assistant', '上次结果')];
    expect(mergeChatHistory([...old, ...current], old, 's')).toEqual([...old, ...current]);
  });
  it('does not carry another session’s local messages into history', () => {
    expect(mergeChatHistory(current, [], 'other', true)).toEqual([]);
  });
});

describe('reentering a persisted conversation', () => {
  const pending = { ...message('u', 'user', '检查订单服务'), metadata: { runId: 'run-1' } };
  it('keeps evidence navigation for completed and failed turns without resubmitting them', () => {
    const answer = { ...message('a', 'assistant', '执行未完成'), metadata: { runId: 'run-1' } };
    expect(latestChatRun([pending, answer], 's')).toBe('run-1');
    expect(recoverableChatRun([pending, answer], 's')).toBe('');
    expect(latestChatRun([pending, answer], 'other')).toBe('');
    expect(latestChatRun([pending, { ...answer, messageId: 'next', metadata: { runId: 'run-2' } }], 's')).toBe('run-2');
  });
  it('does not mistake local placeholders or unrelated-session metadata for durable history', () => {
    const local = { ...message('local-a', 'assistant', '同步中'), metadata: { runId: 'new-run' } };
    expect(latestChatRun([pending, local], 's')).toBe('run-1');
    expect(latestChatRun([message('u', 'user', '没有任务')], 's')).toBe('');
  });
  it('finds the actual unfinished turn without requiring a run ID in the URL', () => {
    expect(recoverableChatRun([pending], 's')).toBe('run-1');
    expect(recoverableChatRun([pending], 'other')).toBe('');
    expect(recoverableChatRun([message('u', 'user', '旧消息')], 's')).toBe('');
  });
  it('does not follow completed or older unanswered turns after a newer message', () => {
    const answer = { ...message('a', 'assistant', '已完成'), metadata: { runId: 'run-1' } };
    expect(recoverableChatRun([pending, answer], 's')).toBe('');
    expect(recoverableChatRun([pending, message('new', 'user', '新消息')], 's')).toBe('');
  });
  it('replaces a restored placeholder only when the same run has a durable answer', () => {
    const restored = [pending, { ...message('local-restored-run-1', 'assistant', '同步中'), metadata: { runId: 'run-1' } }];
    expect(mergeChatHistory(restored, [pending], 's')).toEqual(restored);
    const answer = { ...message('a', 'assistant', '已完成'), metadata: { runId: 'run-1' } };
    expect(mergeChatHistory(restored, [pending, answer], 's')).toEqual([pending, answer]);
    const unrelated = { ...answer, metadata: { runId: 'run-2' } };
    expect(mergeChatHistory(restored, [pending, unrelated], 's')).toEqual(restored);
  });
});
