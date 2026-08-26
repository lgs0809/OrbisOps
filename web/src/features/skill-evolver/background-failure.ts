const reasons: Record<string, string> = {
  SKILL_MODEL_TRANSPORT_DEFERRED: '模型连接暂时异常，任务记录已保留，不影响正常对话。',
  SKILL_EVIDENCE_INPUT_DEFERRED: '原始证据已保留，本轮补读尚不足以完成判断，不影响正常对话。',
  SKILL_AUTHORING_INPUT_TOO_LARGE: '完整证据已保留，但超过当前单次模型输入上限。单纯重试不能缩小输入，需要调整证据读取方式后再处理。',
  SKILL_AUTHORING_SOURCE_TOO_LARGE: '原始记录仍保留，当前来源超过后台读取上限，尚未交给模型分析。',
  SKILL_EVOLUTION_SOURCE_TOO_LARGE: '原始记录仍保留，当前来源超过后台归档上限，尚未完成入队。',
  SKILL_AUTHORING_MODEL_INVALID: '模型没有返回有效的分析结果，原始证据与诊断记录已保留。',
  SKILL_ATOMIC_SOURCE_NOT_ACTIVE: '生成时依据的方法已停用或被替换，旧提案不能继续发布。',
  BACKGROUND_MODEL_TIMEOUT: '后台模型请求超时，进度已保存，不影响正常对话。',
  RERANK_BUDGET_EXCEEDED: '本地重排未在规定时间内完成，后台已暂存，尚未完成方法检索。',
  RETRIEVAL_BUSY: '本地检索服务忙碌，本轮处理尚未完成。',
  BACKGROUND_FAILURE_RECORDED: '最近一次后台处理失败，具体诊断已保存到审计记录。',
};

export const backgroundFailureMessage = (code: unknown): string | undefined => reasons[String(code || "")];
