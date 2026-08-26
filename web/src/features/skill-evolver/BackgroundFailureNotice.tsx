import { backgroundFailureMessage } from './background-failure';

export const BackgroundFailureNotice = ({ job }: { job?: Record<string, any> }) => {
  const diagnostic = backgroundFailureMessage(job?.lastFailureCode);
  const latestReason = backgroundFailureMessage(job?.lastError);
  const message = job?.lastError && (job.lastFailureCode === 'BACKGROUND_FAILURE_RECORDED'
    ? latestReason || diagnostic : diagnostic || latestReason);
  if (!message) return null;
  const recoverable = /^(SKILL_AUTHORING_|SKILL_MODEL_|BACKGROUND_MODEL_|SKILL_EVOLUTION_LEASE_EXPIRED)/.test(String(job?.lastError || ''));
  const at = job?.nextRunAt ? new Date(job.nextRunAt) : null;
  return <div role="status">
    <p>最近处理说明：{message}</p>
    {job?.status === 'FAILED' && <p>{recoverable ? '已保留历史失败，后台会检查权限、来源与模型配置并逐步恢复；恢复后显示下一次尝试时间。' : '已保留失败记录，需处理原来源或权限问题；不会绕过检查继续发布。'}</p>}
    {job?.status === 'PENDING' && at && !Number.isNaN(at.getTime()) && <p>下一次重试不早于：<time dateTime={at.toISOString()}>{at.toLocaleString()}</time>（本地时间）</p>}
  </div>;
};
