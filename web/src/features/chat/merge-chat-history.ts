import type { OpsChatMessage } from '../../services/ops-admin-service';

/** An eventually consistent history response must not erase the active local turn. */
export const mergeChatHistory = (
  current: OpsChatMessage[], history: OpsChatMessage[], sessionId: string, inFlight = false,
): OpsChatMessage[] => {
  const pending = current.filter((item) => item.sessionId === sessionId && item.messageId.startsWith('local-'));
  if (!pending.length) return history;
  if (inFlight) return current;
  const restoredRunId = pending.find((item) => item.messageId.startsWith('local-restored-'))?.metadata?.runId;
  if (restoredRunId) return history.some((item) => item.role === 'assistant'
    && item.metadata?.runId === restoredRunId && Boolean(item.content?.trim())) ? history : current;
  const user = [...pending].reverse().find((item) => item.role === 'user');
  const knownIds = new Set(current.filter((item) => !item.messageId.startsWith('local-')).map((item) => item.messageId));
  const userIndex = history.findIndex((item) => item.role === 'user'
    && item.content === user?.content && !knownIds.has(item.messageId));
  const hasDurableAnswer = userIndex >= 0 && history.slice(userIndex + 1).some((item) =>
    item.role === 'assistant' && Boolean(item.content?.trim()) && !knownIds.has(item.messageId));
  return hasDurableAnswer ? history : current;
};

/** Evidence navigation remains available after a durable turn has finished. */
export const latestChatRun = (history: OpsChatMessage[], sessionId: string): string => {
  const message = [...history].reverse().find((item) => item.sessionId === sessionId
    && !item.messageId.startsWith('local-')
    && typeof item.metadata?.runId === 'string' && item.metadata.runId.trim());
  return typeof message?.metadata?.runId === 'string' ? message.metadata.runId : '';
};

/** Resume only the latest unanswered, server-persisted turn of the current session. */
export const recoverableChatRun = (history: OpsChatMessage[], sessionId: string): string => {
  const scoped = history.filter((item) => item.sessionId === sessionId);
  const user = [...scoped].reverse().find((item) => item.role === 'user');
  const runId = user?.metadata?.runId;
  if (typeof runId !== 'string' || !runId.trim()) return '';
  return scoped.some((item) => item.role === 'assistant' && item.metadata?.runId === runId && item.content?.trim())
    ? '' : runId;
};
