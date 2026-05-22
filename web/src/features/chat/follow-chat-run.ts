export interface ChatRunSnapshot {
  status: string;
  error_message?: string;
  response?: { content?: string };
}

const settled = new Set(['SUCCEEDED', 'FAILED', 'CANCELED', 'WAITING_APPROVAL', 'RECOVERY_REVIEW_REQUIRED']);

/** Read-only continuation after SSE closes. Never submits or resumes a task. */
export async function followChatRun(
  read: () => Promise<ChatRunSnapshot>,
  onSnapshot: (snapshot: ChatRunSnapshot) => void,
  signal: AbortSignal,
  intervalMs = 2000,
  maxWaitMs = 30 * 60 * 1000,
): Promise<ChatRunSnapshot | undefined> {
  const deadline = Date.now() + maxWaitMs;
  let failures = 0;
  while (!signal.aborted) {
    try {
      const snapshot = await read();
      if (signal.aborted) return;
      failures = 0;
      onSnapshot(snapshot);
      if (settled.has(snapshot.status)) return snapshot;
    } catch (error) {
      if (signal.aborted) return;
      if (++failures >= 3) throw error;
    }
    if (Date.now() >= deadline) return;
    await new Promise<void>((resolve) => {
      const finish = () => { clearTimeout(timer); signal.removeEventListener('abort', finish); resolve(); };
      const timer = setTimeout(finish, intervalMs);
      signal.addEventListener('abort', finish, { once: true });
      if (signal.aborted) finish();
    });
  }
}
