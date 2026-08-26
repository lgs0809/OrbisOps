import { structuredAnswerData } from './workflow-tool-answer';

/** A display label only; internal JSON remains in the original task and audit. */
export function taskTitle(goal?: string, fallback = '运行任务'): string {
  const value = goal?.trim() || '';
  if (!value) return fallback;
  const structured = structuredAnswerData(value);
  if (structured) {
    const data = structured.data;
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      const fields = data as Record<string, unknown>;
      const description = [fields.goal, fields.query, fields.alertContent].find(
        (item) => typeof item === 'string' && item.trim() && !/^[\s]*[\[{]/.test(item),
      );
      if (typeof description === 'string') return bounded(description);
      if (typeof fields.serviceId === 'string' && /^[\w.-]{1,100}$/.test(fields.serviceId))
        return `结构化任务 · ${fields.serviceId}`;
    }
    return '结构化输入任务';
  }
  // A server-capped internal payload can stop before closing JSON. Do not turn it
  // into a giant title or infer the result, source, authorization or success.
  if (/^(?:\{\s*"[^"\\]+"\s*:|\[\s*\{)/.test(value)) return '结构化输入任务';
  return bounded(value);
}

function bounded(value: string) {
  const plain = value.replace(/\s+/g, ' ').trim();
  return plain.length > 120 ? `${plain.slice(0, 119)}…` : plain;
}
