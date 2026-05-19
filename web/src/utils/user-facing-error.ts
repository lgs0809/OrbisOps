const INTERNAL_CODE_MESSAGES: Record<string, string> = {
  INCIDENT_PROJECT_ID_REQUIRED: '请先选择 Project。',
  PROJECT_ID_REQUIRED: '请先选择 Project。',
  KNOWLEDGE_BASE_ID_REQUIRED: '请先选择知识库。',
  MODEL_API_ID_REQUIRED: '请先选择 Provider。',
};

const looksLikeInternalCode = (message: string) => /^[A-Z][A-Z0-9_]{5,}$/.test(message);

const containsUnsafeTechnicalDetail = (message: string) => {
  const normalized = message.toLowerCase();
  return normalized.includes('http error!')
    || normalized.includes('network request failed')
    || normalized.includes('stack trace')
    || normalized.includes('java.lang.')
    || normalized.includes('org.springframework.')
    || normalized.includes('exception:')
    || /https?:\/\/[^\s]+/i.test(message)
    || /\b(?:get|post|put|patch|delete)\s+\/api\//i.test(message)
    || /\bstatus\s*:\s*\d{3}\b/i.test(message)
    || /\bhttp\s*\d{3}\b/i.test(message);
};

/**
 * Convert transport/runtime failures into copy that is safe to show in the product UI.
 * Detailed diagnostics stay in logs/audit/technical-detail surfaces rather than toast copy.
 */
export const userFacingError = (error: unknown, fallback: string): string => {
  const message = error instanceof Error ? error.message.trim() : '';
  if (!message) return fallback;

  if (INTERNAL_CODE_MESSAGES[message]) return INTERNAL_CODE_MESSAGES[message];
  if (looksLikeInternalCode(message) || containsUnsafeTechnicalDetail(message)) return fallback;

  // Keep intentional, human-readable application errors concise. Very long messages are
  // usually protocol/provider diagnostics and belong in the technical detail surface.
  if (message.length > 180) return fallback;
  return message;
};

export const userFacingDetail = (message: unknown, fallback = '操作未完成，请稍后重试。'): string => {
  if (typeof message !== 'string' || !message.trim()) return fallback;
  return userFacingError(new Error(message), fallback);
};
