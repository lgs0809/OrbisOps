export type AuthoredDecision = {
  planId: string; operation: string; reason: string; source: string; model: string; currentSource: boolean;
};
type Job = { status?: string; authoredDecision?: AuthoredDecision };

/** A completed no-change conclusion is independent of publication and the historical patch label. */
export const currentNoChange = (job?: Job): AuthoredDecision | undefined =>
  job?.status === 'SKIPPED' && job.authoredDecision?.currentSource === true
    && job.authoredDecision.operation === 'NO_CHANGE' ? job.authoredDecision : undefined;

export const AuthoredDecisionNotice = ({ job }: { job?: Job }) => {
  const decision = currentNoChange(job);
  if (!decision) return null;
  return <section aria-label="后台分析结论">
    <p>后台分析已完成：无需变更，原有方法与本次经验保留。</p>
    {decision.reason && <p>{decision.reason}</p>}
  </section>;
};
