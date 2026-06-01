type Publication = { status: string; operation: string; releasedVersion: number; reason: string };

const labels: Record<string, string> = {
  CANDIDATE: '提案已保存，等待检查', READY: '检查通过，等待发布',
  PENDING_INDEX: '版本已保存，等待检索就绪', STAGED: '整组版本已保存，等待检索就绪',
  ACTIVE: '方法已发布并生效', VALIDATION_FAILED: '提案检查未通过', POLICY_REJECTED: '提案未通过安全检查',
};

/** Authoring job outcome and publication outcome are independent, read-only facts. */
export const AuthoredPublicationNotice = ({ publication }: { publication?: Publication }) => {
  if (!publication) return null;
  const closed = publication.status === 'ROLLED_BACK' && publication.releasedVersion === 0;
  const label = closed ? '提案已关闭（未发布）'
    : publication.status === 'ROLLED_BACK' ? '方法已回滚' : labels[publication.status] || '发布状态待核对';
  return <section aria-label="已保存提案的当前状态">
    <p>已保存提案：{label}</p>
    {publication.reason === 'BASELINE_STALE' && <p>生成时依据的方法或任务证据已变化，后台已关闭这份旧提案。原文与失败记录仍保留。</p>}
  </section>;
};
