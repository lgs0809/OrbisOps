type JsonObject = Record<string, unknown>;
const object = (value: unknown): value is JsonObject => !!value && typeof value === 'object' && !Array.isArray(value);

/** Presentation only: parsing a result does not verify its source, authorization or outcome. */
export function structuredAnswerData(text: string): { data: unknown } | undefined {
  if (text.length > 1_048_576 || !/^[\s]*[\[{]/.test(text)) return undefined;
  try {
    const data: unknown = JSON.parse(text);
    return object(data) || Array.isArray(data) ? { data } : undefined;
  } catch {
    return undefined;
  }
}

export interface WorkflowToolAnswer {
  toolName?: string;
  evidenceRef?: string;
  data: unknown;
  isError: boolean;
  truncated: boolean;
}

/** Recognize the governed MCP envelope, never arbitrary business JSON or model prose. */
export function workflowToolAnswer(text: string): WorkflowToolAnswer | undefined {
  if (text.length > 1_048_576 || !text.trimStart().startsWith('{')) return undefined;
  try {
    const value: unknown = JSON.parse(text);
    if (!object(value) || !object(value.mcpEnvelope)) return undefined;
    const envelope = value.mcpEnvelope;
    if (envelope.orbisopsResultVersion !== 1 || typeof envelope.isError !== 'boolean'
      || !Object.prototype.hasOwnProperty.call(envelope, 'normalizedContent')) return undefined;
    // Authorization failure remains its original answer, rather than a data receipt.
    if (value.allowed === false || (value.decision !== undefined && value.decision !== 'ALLOWED')) return undefined;
    return {
      toolName: typeof value.remoteToolName === 'string' ? value.remoteToolName
        : typeof value.toolName === 'string' ? value.toolName : undefined,
      evidenceRef: typeof value.fullOutputRef === 'string' ? value.fullOutputRef : undefined,
      data: envelope.normalizedContent,
      isError: envelope.isError,
      truncated: value.truncated === true,
    };
  } catch {
    return undefined;
  }
}

/** A bounded view of data; field names, false/zero/null and provider wording remain literal. */
export function toolAnswerRows(data: unknown) {
  const rows: Array<{ field: string; value: string }> = [];
  let omitted = false;
  const append = (field: string, value: unknown) => {
    const raw = typeof value === 'string' ? value : JSON.stringify(value) ?? String(value);
    if (raw.length > 4096) omitted = true;
    rows.push({ field: field || '返回值', value: raw.length > 4096 ? raw.slice(0, 4096) + '…' : raw });
  };
  const visit = (value: unknown, path: string, depth: number) => {
    if (rows.length >= 40) { omitted = true; return; }
    const entries = Array.isArray(value) ? value.map((item, index) => [`[${index}]`, item] as const)
      : object(value) ? Object.entries(value) : [];
    if (!entries.length || depth >= 4) { append(path, value); return; }
    for (const [key, child] of entries) {
      if (rows.length >= 40) { omitted = true; break; }
      visit(child, path ? `${path}${key.startsWith('[') ? '' : '.'}${key}` : key, depth + 1);
    }
  };
  visit(data, '', 0);
  return { rows, omitted };
}
